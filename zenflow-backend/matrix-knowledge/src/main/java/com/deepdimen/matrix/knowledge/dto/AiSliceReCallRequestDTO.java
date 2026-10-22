package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

/**
 * AI文档切片召回请求数据传输对象
 * <p>
 * 用于传输文档切片检索和重排序的请求参数，支持全文检索、向量检索和重排序等功能
 *
 * @author caoyuan
 * @date 2025/4/14
 */
@Data
public class AiSliceReCallRequestDTO {

	/**
	 * 数据集ID
	 * <p>
	 * 指定要检索的数据集的唯一标识符
	 */
	private Long datasetId;

	/**
	 * 查询内容
	 * <p>
	 * 用户输入的查询文本，用于向量检索和相似度匹配
	 */
	private String content;

	/**
	 * 全文检索开关
	 * <p>
	 * 是否启用全文检索功能，"1"表示启用，"0"表示禁用
	 */
	private String fullTextSearch;

	/**
	 * 重排序开关
	 * <p>
	 * 是否启用结果重排序功能，"1"表示启用，"0"表示禁用
	 */
	private String reRanking;

	/**
	 * 返回结果数量
	 * <p>
	 * 指定返回的最相关文档切片数量，即Top-K个结果
	 */
	private Integer topK;

}
