package com.deepdimen.matrix.knowledge.support.handler.websearch;

import lombok.Builder;
import lombok.Data;
import lombok.Setter;

/**
 * 搜索请求参数类
 * <p>
 * 封装BoCha搜索API的请求参数 使用Builder模式构建，支持灵活配置搜索选项
 *
 * @author caoyuan
 * @date 2025/06/20
 */
@Builder
@Data
public class SearchRequest {

	/**
	 * 是否启用搜索
	 */
	private final boolean enable;

	/**
	 * 搜索结果时间范围限制 默认无时间限制
	 */
	@Builder.Default
	private final FreshnessEnums freshness = FreshnessEnums.NO_LIMIT;

	/**
	 * 是否生成搜索结果摘要 默认启用摘要功能
	 */
	@Builder.Default
	private final boolean summary = true;

	/**
	 * 返回结果数量 默认返回10条结果
	 */
	@Builder.Default
	private final int count = 10;

	/**
	 * 分页页码 默认第1页
	 */
	@Builder.Default
	private final int page = 1;

	/**
	 * 搜索关键词
	 */
	@Setter
	private String query;

}
