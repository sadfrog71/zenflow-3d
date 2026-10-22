package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

/**
 * AI文档切片召回数据传输对象
 * <p>
 * 用于封装向量检索召回的文档切片信息，包含切片内容、相关性评分等
 *
 * @author caoyuan
 * @date 2025/4/14
 */
@Data
public class AiSliceReCallDTO {

	/**
	 * 文档切片ID
	 * <p>
	 * 召回的文档切片的唯一标识符
	 */
	private Long sliceId;

	/**
	 * 相关性评分
	 * <p>
	 * 表示切片与查询的相关程度，分数越高相关性越强
	 */
	private String score;

	/**
	 * 切片内容
	 * <p>
	 * 文档切片的实际文本内容
	 */
	private String content;

	/**
	 * 元数据信息
	 * <p>
	 * 切片的附加信息，如来源文档、页码、创建时间等，通常为JSON格式
	 */
	private String metadata;

}
