package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

/**
 * AI海报生成数据传输对象
 * <p>
 * 用于传输生成AI海报所需的参数信息，包括模型配置、模板选择和生成提示等
 *
 * @author caoyuan
 * @date 2025/4/4
 */
@Data
@FieldNameConstants
public class AiPosterDTO {

	/**
	 * AI模型名称
	 * <p>
	 * 指定用于生成海报的AI模型（如：GPT-4、DALL-E等）
	 */
	private String modelName;

	/**
	 * 海报模板ID
	 * <p>
	 * 预设海报模板的唯一标识符，用于选择特定的海报样式
	 */
	public String templateId;

	/**
	 * 二维码内容
	 * <p>
	 * 需要嵌入到海报中的二维码数据（如：URL、文本等）
	 */
	public String qrCode;

	/**
	 * 生成提示词
	 * <p>
	 * 用于指导AI生成海报内容的详细描述或关键词
	 */
	public String prompt;

}
