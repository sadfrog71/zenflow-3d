package com.deepdimen.matrix.work.api.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TenantTable
@TableName("ai_information_tavily_config")
public class InformationTavilyConfig {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private Integer enabled;
	/**
	 * Must be written as NULL when the administrator explicitly clears the key.
	 * MyBatis-Plus otherwise skips null updates and leaves the old secret behind.
	 */
	@TableField(updateStrategy = FieldStrategy.ALWAYS)
	private String apiKeyCipher;
	private String baseUrl;
	private String searchDepth;
	private Integer maxResults;
	private Integer timeoutSeconds;

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
