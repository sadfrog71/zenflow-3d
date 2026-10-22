package com.deepdimen.matrix.capsule.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.deepdimen.matrix.capsule.api.enums.CapsuleRecordType;
import com.deepdimen.matrix.capsule.api.enums.CapsuleStatus;
import com.deepdimen.matrix.common.core.util.TenantTable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TenantTable
@TableName("ai_capsule")
public class CapsuleEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long tenantId;

    private Long ownerUserId;

    private String ownerSubjectType;

    private CapsuleRecordType recordType;

    private String title;

    private Long currentRevisionId;

    private String sourceType;

    private String sourceLabel;

    private String tags;

    private CapsuleStatus status;

    private String favoriteFlag;

    private LocalDateTime capturedAt;

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
}
