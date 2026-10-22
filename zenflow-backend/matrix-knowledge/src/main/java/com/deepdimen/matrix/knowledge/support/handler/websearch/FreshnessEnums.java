package com.deepdimen.matrix.knowledge.support.handler.websearch;

/**
 * 搜索结果新鲜度枚举
 * <p>
 * 定义网络搜索结果的时间范围限制 用于筛选指定时间范围内的搜索结果
 *
 * @author caoyuan
 * @date 2025/06/20
 */
public enum FreshnessEnums {

	/**
	 * 一天内的结果
	 */
	ONE_DAY("oneDay"),

	/**
	 * 一周内的结果
	 */
	ONE_WEEK("oneWeek"),

	/**
	 * 一个月内的结果
	 */
	ONE_MONTH("oneMonth"),

	/**
	 * 一年内的结果
	 */
	ONE_YEAR("oneYear"),

	/**
	 * 无时间限制
	 */
	NO_LIMIT("noLimit");

	/**
	 * 枚举值
	 */
	private final String value;

	FreshnessEnums(String value) {
		this.value = value;
	}

}
