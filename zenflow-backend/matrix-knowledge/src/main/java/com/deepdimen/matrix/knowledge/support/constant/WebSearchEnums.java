package com.deepdimen.matrix.knowledge.support.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 网络搜索引擎枚举
 * <p>
 * 定义了系统支持的网络搜索引擎类型，用于增强AI回答的实时性和准确性
 *
 * @author caoyuan
 * @date 2025/2/9
 */
@Getter
@RequiredArgsConstructor
public enum WebSearchEnums {

	/**
	 * Bocha搜索引擎 - 基于博查的网络搜索服务
	 */
	BOCHA("bocha-web-search", "Search"),

	/**
	 * SearXNG搜索引擎 - 开源的元搜索引擎，聚合多个搜索引擎结果
	 */
	SEARXNG("sear-xng", "Search"),

	/**
	 * OLLAMA工具标识符
	 */
	OLLAMA("ollama-web-search", "Search");

	/**
	 * 搜索引擎名称
	 */
	private final String name;

	/**
	 * 搜索引擎类型
	 */
	private final String type;

}
