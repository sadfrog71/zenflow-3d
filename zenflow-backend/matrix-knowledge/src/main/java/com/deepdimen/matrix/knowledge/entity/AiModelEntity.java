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
 * 模型配置
 *
 * @author matrix
 * @date 2024-09-27 23:37:54
 */
@Data
@TableName("ai_model")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "模型配置")
@TenantTable
public class AiModelEntity extends Model<AiModelEntity> {

	/**
	 * 主键ID
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键ID")
	private Long id;

	/**
	 * 类型: CHAT、Embedding、Image
	 */
	@Schema(description = "类型: CHAT、Embedding、Image")
	private String modelType;

	/**
	 * 模型名称
	 */
	@Schema(description = "模型名称")
	private String modelName;

	/**
	 * 供应商
	 */
	@Schema(description = "供应商")
	private String provider;

	/**
	 * 默认模型
	 */
	@Schema(description = "是否默认模型")
	private String defaultModel;

	/**
	 * 是否用于统一助手。该标记只允许设置在当前租户的 Chat 模型上。
	 */
	@Schema(description = "是否统一助手模型")
	private String assistantModel;

	/**
	 * 别名
	 */
	@Schema(description = "别名")
	private String name;

	/**
	 * 响应长度
	 */
	@Schema(description = "响应长度")
	private Integer responseLimit;

	/**
	 * 温度
	 */
	@Schema(description = "温度")
	private Double temperature;

	/**
	 * topP
	 */
	@Schema(description = "topP")
	private Double topP;

	/**
	 * apiKey
	 */
	@ExcelIgnore
	@Sensitive(type = SensitiveTypeEnum.KEY)
	@Schema(description = "apiKey")
	private String apiKey;

	/**
	 * baseUrl
	 */
	@Schema(description = "baseUrl")
	private String baseUrl;

	/**
	 * secretKey
	 */
	@ExcelIgnore
	@Sensitive(type = SensitiveTypeEnum.KEY)
	@Schema(description = "secretKey")
	private String secretKey;

	/**
	 * endpoint
	 */
	@Schema(description = "endpoint")
	private String endpoint;

	/**
	 * azure模型参数
	 */
	@Schema(description = "azure模型参数")
	private String azureDeploymentName;

	/**
	 * gemini模型参数
	 */
	@Schema(description = "gemini模型参数")
	private String geminiProject;

	/**
	 * gemini模型参数
	 */
	@Schema(description = "gemini模型参数")
	private String geminiLocation;

	/**
	 * 图片大小
	 */
	@Schema(description = "图片大小")
	private String imageSize;

	/**
	 * 图片质量
	 */
	@Schema(description = "图片质量")
	private String imageQuality;

	/**
	 * 图片风格
	 */
	@Schema(description = "图片风格")
	private String imageStyle;

	/**
	 * 向量维数
	 */
	@Schema(description = "向量维数")
	private Integer dimensions;

	/**
	 * ext 数据
	 */
	@Schema(description = "扩展数据")
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
	 * 修改人
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "修改人")
	private String updateBy;

	/**
	 * 修改时间
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	/**
	 * 删除标记
	 */
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "删除标记")
	private String delFlag;

}
