package com.deepdimen.matrix.work.notification;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.data.tenant.TenantBroker;
import com.deepdimen.matrix.work.entity.WorkTaskEntity;
import com.deepdimen.matrix.work.mapper.WorkTaskMapper;
import com.deepdimen.matrix.work.service.WorkTaskV2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnExpression("${matrix.work.task-workers-enabled:false} && ${matrix.work.task-maintenance-enabled:true}")
public class TaskMaintenanceWorker {
    private final WorkTaskMapper tasks;
    private final WorkTaskV2Service commands;
    private long cursor;
    @Scheduled(fixedDelayString="${matrix.work.maintenance-delay-ms:60000}")
    public void tick() {
        var batch=TenantBroker.noneAs(()->tasks.selectList(Wrappers.<WorkTaskEntity>lambdaQuery()
            .eq(WorkTaskEntity::getTaskContractVersion,2).gt(WorkTaskEntity::getId,cursor)
            .notIn(WorkTaskEntity::getStatus,java.util.List.of("DONE","CANCELLED"))
            .orderByAsc(WorkTaskEntity::getId).last("LIMIT 100")));
        for(var task:batch){
            try{TenantBroker.runAs(task.getTenantId(),tenant->commands.maintain(tenant,task.getId()));}
            catch(Exception ex){log.warn("Task maintenance deferred for task {}",task.getId());}
            cursor=task.getId();
        }
        if(batch.size()<100)cursor=0;
    }
}
