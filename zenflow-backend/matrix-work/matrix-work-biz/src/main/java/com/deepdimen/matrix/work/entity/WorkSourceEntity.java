package com.deepdimen.matrix.work.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import com.deepdimen.matrix.work.api.enums.WorkSourceType;
import java.time.LocalDateTime;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@TenantTable
@TableName("ai_work_source")
public class WorkSourceEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

	private Long orgId;

	private Long ownerUserId;

    private String name;

    private WorkSourceType sourceType;

    private String protocolType;

    private Long configRefId;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String secretRef;

    private String configJson;

    private Boolean enabled;

    private String healthStatus;

    private LocalDateTime lastSyncAt;

	private String syncCursor;

    private String lastError;

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
