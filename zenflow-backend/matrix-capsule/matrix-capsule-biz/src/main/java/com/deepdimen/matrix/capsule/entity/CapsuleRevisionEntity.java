package com.deepdimen.matrix.capsule.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TenantTable
@TableName("ai_capsule_revision")
public class CapsuleRevisionEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long capsuleId;

    private Long tenantId;

    private Long ownerUserId;

    private String ownerSubjectType;

    private Integer revisionNo;

    private String contentFormat;

    private String content;

    private String contentHash;

    private String changeReason;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
