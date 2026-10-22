package com.deepdimen.matrix.work.notification;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.dto.BusinessMessageRequest;
import com.deepdimen.matrix.admin.api.dto.MessageEmailDTO;
import com.deepdimen.matrix.admin.api.service.BusinessMessagePublisher;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.admin.mapper.SysUserMapper;
import com.deepdimen.matrix.admin.service.SysMessageService;
import com.deepdimen.matrix.common.data.tenant.TenantBroker;
import com.deepdimen.matrix.work.ai.TaskPolicyService;
import com.deepdimen.matrix.work.entity.WorkTaskEventEntity;
import com.deepdimen.matrix.work.mapper.WorkTaskEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnExpression("${matrix.work.task-workers-enabled:false} && ${matrix.work.task-notifications-enabled:true}")
public class TaskNotificationWorker {
    private final WorkTaskEventMapper events;
    private final BusinessMessagePublisher publisher;
    private final TenantUserDirectory directory;
    private final TaskPolicyService policies;
    private final SysMessageService smtp;
    private final SysUserMapper users;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Scheduled(fixedDelayString="${matrix.work.notification-delay-ms:10000}")
    public void tick() {
        var ready=TenantBroker.noneAs(()->events.selectList(Wrappers.<WorkTaskEventEntity>lambdaQuery()
            .in(WorkTaskEventEntity::getPublishStatus,List.of("PENDING","RETRY","PROCESSING"))
            .and(q->q.isNull(WorkTaskEventEntity::getNextAttemptAt).or().le(WorkTaskEventEntity::getNextAttemptAt,now()))
            .and(q->q.isNull(WorkTaskEventEntity::getLeaseUntil).or().lt(WorkTaskEventEntity::getLeaseUntil,now()))
            .orderByAsc(WorkTaskEventEntity::getOccurredAt).last("LIMIT 20")));
        for(var event:ready) if(event.getTenantId()!=null) {
            try {TenantBroker.runAs(event.getTenantId(),tenant->deliver(event));}
            catch(Exception ex){log.warn("Task notification retry deferred for event {}",event.getId());}
        }
    }

    public void deliver(WorkTaskEventEntity event) throws Exception {
        String lease=UUID.randomUUID().toString();
        var claim=Wrappers.<WorkTaskEventEntity>lambdaUpdate().eq(WorkTaskEventEntity::getTenantId,event.getTenantId())
            .eq(WorkTaskEventEntity::getId,event.getId()).in(WorkTaskEventEntity::getPublishStatus,List.of("PENDING","RETRY","PROCESSING"))
            .and(q->q.isNull(WorkTaskEventEntity::getLeaseUntil).or().lt(WorkTaskEventEntity::getLeaseUntil,now()))
            .set(WorkTaskEventEntity::getLeaseOwner,lease).set(WorkTaskEventEntity::getLeaseUntil,now().plusMinutes(5))
            .set(WorkTaskEventEntity::getPublishStatus,"PROCESSING");
        if(events.update(null,claim)!=1)return;
        int attempt=(event.getAttempts()==null?0:event.getAttempts())+1;
        try {
            var payload=json.readTree(event.getPayloadJson());
            List<Long> recipients=new ArrayList<>();
            for(var id:payload.path("recipientUserIds")) if(id.canConvertToLong()||id.isTextual()) {
                Long user=Long.valueOf(id.asText());if(directory.isAssignableUser(event.getTenantId(),user))recipients.add(user);
            }
            // Keep notification content minimal: access to the task is checked again on opening.
            String title="任务 " + event.getTaskId() + " 有新的处理进展";
            boolean actionRequired=Set.of("TASK_ASSIGNED","TASK_SUBMITTED","TASK_RETURNED","TASK_TRANSFER_REQUESTED","TASK_NUDGED","TASK_OVERDUE","TASK_DUE_SOON","TASK_ACCEPTANCE_STALE","TASK_RESPONSIBLE_INACTIVE","TASK_DISPATCH_FAILED").contains(event.getType());
            Long messageId=null;
            if(!recipients.isEmpty())messageId=publisher.publish(BusinessMessageRequest.builder()
                .bizKey("task-event:"+event.getId()).sourceType("TASK").sourceId(String.valueOf(event.getTaskId()))
                .eventType(event.getType()).title(title).content("请进入任务中心查看当前内容和处理要求。")
                .priority(actionRequired?"P1":"P2").actionRequired(actionRequired).targetPath("/tasks")
                .targetQuery(Map.of("taskId",String.valueOf(event.getTaskId()))).recipientUserIds(recipients).build());
            var policy=policies.current(event.getTenantId());
            if(Boolean.TRUE.equals(policy.getEmailEnabled()))for(Long user:recipients) email(event,user,policy.getEmailBizCode(),policy.getWebBaseUrl(),title);
            Long uncertain = jdbc.queryForObject("SELECT COUNT(*) FROM sys_business_message_delivery WHERE tenant_id=? AND biz_key=? AND status='UNKNOWN'",Long.class,event.getTenantId(),"task-event:"+event.getId());
            events.update(null,owned(event,lease).set(WorkTaskEventEntity::getPublishStatus,uncertain != null && uncertain > 0 ? "PARTIAL" : "PUBLISHED")
                .set(WorkTaskEventEntity::getAttempts,attempt).set(WorkTaskEventEntity::getMessageRefs,messageId==null?"[]":"["+messageId+"]")
                .set(WorkTaskEventEntity::getLeaseUntil,null).set(WorkTaskEventEntity::getLastError,null));
        } catch(Exception ex) {
            events.update(null,owned(event,lease).set(WorkTaskEventEntity::getPublishStatus,attempt>=8?"FAILED":"RETRY")
                .set(WorkTaskEventEntity::getAttempts,attempt).set(WorkTaskEventEntity::getNextAttemptAt,now().plusMinutes(Math.min(60,1L<<Math.min(attempt,6))))
                .set(WorkTaskEventEntity::getLeaseUntil,null).set(WorkTaskEventEntity::getLastError,"DELIVERY_FAILED"));
        }
    }

    private void email(WorkTaskEventEntity event,Long recipient,String bizCode,String baseUrl,String title) {
        String key="task-event:"+event.getId();
        jdbc.update("INSERT IGNORE INTO sys_business_message_delivery (tenant_id,biz_key,recipient_id,channel,status,attempts) VALUES (?,?,?,'EMAIL','PENDING',0)",event.getTenantId(),key,recipient);
        String status=jdbc.queryForObject("SELECT status FROM sys_business_message_delivery WHERE tenant_id=? AND biz_key=? AND recipient_id=? AND channel='EMAIL'",String.class,event.getTenantId(),key,recipient);
        if(List.of("SENT","SKIPPED","UNKNOWN").contains(status))return;
        if(!directory.isAssignableUser(event.getTenantId(),recipient))return;
        var user=TenantBroker.noneAs(()->users.selectById(recipient));
        if(user==null||user.getEmail()==null||user.getEmail().isBlank()) {deliveryState(event,key,recipient,"SKIPPED");return;}
        // UNKNOWN is written before SMTP: a process crash must not blindly send the same email twice.
        // Operations can investigate and retry an uncertain delivery explicitly.
        deliveryState(event,key,recipient,"UNKNOWN");
        MessageEmailDTO mail=new MessageEmailDTO();mail.setBizCode(bizCode);mail.setMailAddress(List.of(user.getEmail()));mail.setTitle(title);
        mail.setHtmlValues(Map.of("title",HtmlUtils.htmlEscape(title),"content","请进入任务中心查看当前内容和处理要求。",
            "taskUrl",HtmlUtils.htmlEscape(baseUrl.replaceAll("/$","")+"/#/tasks?taskId="+event.getTaskId())));
        var result=smtp.sendEmail(mail);
        if(result==null||result.getCode()!=0) {deliveryState(event,key,recipient,"PENDING");throw new IllegalStateException("EMAIL_REJECTED");}
        deliveryState(event,key,recipient,"SENT");
    }
    private void deliveryState(WorkTaskEventEntity e,String key,Long user,String state){jdbc.update("UPDATE sys_business_message_delivery SET status=?,attempts=attempts+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND biz_key=? AND recipient_id=? AND channel='EMAIL'",state,e.getTenantId(),key,user);}
    private com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkTaskEventEntity> owned(WorkTaskEventEntity event,String lease){return Wrappers.<WorkTaskEventEntity>lambdaUpdate().eq(WorkTaskEventEntity::getTenantId,event.getTenantId()).eq(WorkTaskEventEntity::getId,event.getId()).eq(WorkTaskEventEntity::getLeaseOwner,lease).eq(WorkTaskEventEntity::getPublishStatus,"PROCESSING");}
    private static LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));}
}
