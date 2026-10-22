package com.deepdimen.matrix.knowledge.config.properties;

import lombok.Data;

/**
 * 全文搜索配置属性
 *
 * @author caoyuan
 * @date 2025/02/27
 */
@Data
public class FullTextSearchProperties {

	/**
	 * 是否启用全文搜索
	 */
	private boolean enabled = true;

	/**
	 * 返回的最大结果数
	 */
	private int topK = 1;

	/**
	 * 搜索算法：允许在搜索过程中对查询向量中的小值进行微调
	 */
	private String algorithm = "drop_ratio_search";

	/**
	 * 丢弃比率（用于优化搜索精度）
	 */
	private float dropRatio = 0.2F;

}
