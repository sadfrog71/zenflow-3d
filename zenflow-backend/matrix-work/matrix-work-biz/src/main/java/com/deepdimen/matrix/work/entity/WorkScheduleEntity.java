package com.deepdimen.matrix.work.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.deepdimen.matrix.common.core.util.TenantTable;
import com.deepdimen.matrix.work.api.enums.WorkScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TenantTable
@TableName("ai_work_schedule")
@Schema(description = "Pig AI 内部日程")
public class WorkScheduleEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long targetUserId;

    private String title;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private String timezone;

    private String location;

    private String meetingUrl;

    private String organizer;

    private WorkScheduleStatus status;

    private String calendarUid;

    private String calendarUidHash;

    private String recurrenceId;

    private Integer sequenceNo;

    private Long sourceId;

    private String sourceRefId;

    private String evidenceRefs;

    private LocalDateTime acknowledgedAt;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private String delFlag;

    private Long tenantId;
}
