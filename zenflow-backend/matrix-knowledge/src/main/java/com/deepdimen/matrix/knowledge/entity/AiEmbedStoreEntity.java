package com.deepdimen.matrix.knowledge.entity;

import cn.idev.excel.annotation.ExcelIgnore;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import com.deepdimen.matrix.common.sensitive.annotation.Sensitive;
import com.deepdimen.matrix.common.sensitive.core.SensitiveTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 向量库配置
 *
 * @author matrix
 * @date 2025-02-11 15:07:46
 */
@Data
@TableName("ai_embed_store")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "向量库配置")
@TenantTable
public class AiEmbedStoreEntity extends Model<AiEmbedStoreEntity> {

	/**
	 * 主键
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键")
	private Long storeId;

	/**
	 * 名称
	 */
	@Schema(description = "名称")
	private String name;

	/**
	 * 类型
	 */
	@Schema(description = "类型")
	private String storeType;

	/**
	 * URI
	 */
	@Schema(description = "uri")
	private String uri;

	/**
	 * Host
	 */
	@Schema(description = "Host")
	private String host;

	/**
	 * 端口
	 */
	@Schema(description = "端口")
	private Integer port;

	/**
	 * API 密钥
	 */
	@ExcelIgnore
	@Sensitive(type = SensitiveTypeEnum.KEY)
	@Schema(description = "API 密钥")
	private String apiKey;

	/**
	 * 使用 TLS
	 */
	@Schema(description = "使用 TLS")
	private String useTls;

	/**
	 * 扩展参数
	 */
	@Schema(description = "扩展参数")
	private String extData;

	/**
	 * 创建人
	 */
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	/**
	 * 创建时间
	 */
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	/**
	 * 更新人
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "更新人")
	private String updateBy;

	/**
	 * 更新时间
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	/**
	 * 删除标记
	 */
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "删除标记")
	private String delFlag;

}
