package com.deepdimen.matrix.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 数据源表
 *
 * @author matrix
 * @date 2025-09-21 17:25:49
 */
@Data
@TenantTable
@TableName("ai_datasource")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "数据源表")
public class AiDatasourceEntity extends Model<AiDatasourceEntity> {

	/**
	 * 主键
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键")
	private Long id;

	/**
	 * 别名
	 */
	@Schema(description = "别名")
	private String name;

	/**
	 * jdbcurl
	 */
	@Schema(description = "jdbcurl")
	private String url;

	/**
	 * 用户名
	 */
	@Schema(description = "用户名")
	private String username;

	/**
	 * 密码 ，jackson 不序列化这个字段
	 */
	@Schema(description = "密码")
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String password;

	/**
	 * 数据库类型
	 */
	@Schema(description = "数据库类型")
	private String dsType;

	/**
	 * 创建时间
	 */
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	/**
	 * 更新
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "更新")
	private LocalDateTime updateTime;

	/**
	 * 删除标记
	 */
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "删除标记")
	private String delFlag;

}
