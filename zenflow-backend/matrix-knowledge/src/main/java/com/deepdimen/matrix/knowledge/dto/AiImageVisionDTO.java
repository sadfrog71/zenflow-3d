package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

/**
 * AI图片视觉识别数据传输对象
 *
 * @author caoyuan
 * @date 2025/09/14
 */
@Data
@FieldNameConstants
public class AiImageVisionDTO {

	/** 图片的Base64编码 */
	private String imageBase64;

	/** 模型名称 */
	private String modelName;

	/** 安全级别 */
	private Integer securityLevel;

}
