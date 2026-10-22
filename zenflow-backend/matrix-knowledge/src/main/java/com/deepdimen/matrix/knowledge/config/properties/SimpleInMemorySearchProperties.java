package com.deepdimen.matrix.knowledge.config.properties;

import lombok.Data;

/**
 * 内存向量搜索配置
 *
 * @author caoyuan
 * @date 2025/03/21
 */
@Data
public class SimpleInMemorySearchProperties {

	/**
	 * 返回的最相似结果数量
	 */
	private int topK = 1;

	/**
	 * 最低相似度分数阈值
	 */
	private double minScore = 0.5;

	/**
	 * 文本分段最大字符数
	 */
	private int maxSegmentSizeInChars = 500;

	/**
	 * 分段重叠字符数
	 */
	private int maxOverlapSizeInChars = 50;

}
