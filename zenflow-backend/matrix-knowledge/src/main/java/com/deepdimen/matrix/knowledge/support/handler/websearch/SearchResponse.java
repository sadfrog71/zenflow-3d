package com.deepdimen.matrix.knowledge.support.handler.websearch;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 搜索响应结果类
 * <p>
 * 封装BoCha搜索API的响应数据结构 包含搜索结果的状态码、日志信息和具体搜索内容
 *
 * @author caoyuan
 * @date 2025/06/20
 */
@Data
public class SearchResponse {

	/**
	 * 响应状态码 200表示成功
	 */
	private Integer code;

	/**
	 * 请求日志ID 用于追踪和调试
	 */
	@JsonProperty("log_id")
	private String logId;

	/**
	 * 响应消息 通常包含错误信息或状态描述
	 */
	private String msg;

	/**
	 * 响应数据主体 包含实际的搜索结果
	 */
	private ResponsData data;

	/**
	 * 响应数据内部类
	 * <p>
	 * 包含搜索结果的详细内容，支持网页和图片两种搜索类型
	 */
	@Data
	public static class ResponsData {

		/**
		 * 数据类型标识
		 */
		private String _type;

		/**
		 * 查询上下文信息 包含原始查询内容
		 */
		private QueryContext queryContext;

		/**
		 * 网页搜索结果
		 */
		private WebPages webPages;

		/**
		 * 图片搜索结果
		 */
		private Images images;

	}

	/**
	 * 查询上下文类
	 * <p>
	 * 保存用户的原始查询信息
	 */
	@Data
	public static class QueryContext {

		/**
		 * 用户原始查询内容
		 */
		private String originalQuery;

	}

	/**
	 * 网页搜索结果类
	 * <p>
	 * 封装网页搜索的结果集和相关元数据
	 */
	@Data
	public static class WebPages {

		/**
		 * 网页搜索的完整URL
		 */
		private String webSearchUrl;

		/**
		 * 预估的匹配结果总数
		 */
		private Integer totalEstimatedMatches;

		/**
		 * 搜索结果数组 包含具体的网页信息
		 */
		private Value[] value;

		/**
		 * 是否因某些原因移除了部分结果 如内容审查、版权等
		 */
		private Boolean someResultsRemoved;

	}

	/**
	 * 搜索结果项
	 * <p>
	 * 表示单个搜索结果的详细信息
	 */
	@Data
	public static class Value {

		/**
		 * 结果唯一标识
		 */
		private String id;

		/**
		 * 网页标题
		 */
		private String name;

		/**
		 * 网页完整URL
		 */
		private String url;

		/**
		 * 显示用的简化URL
		 */
		private String displayUrl;

		/**
		 * 搜索结果片段 通常包含匹配关键词的上下文
		 */
		private String snippet;

		/**
		 * AI生成的内容摘要
		 */
		private String summary;

		/**
		 * 网站名称
		 */
		private String siteName;

		/**
		 * 网站图标URL
		 */
		private String siteIcon;

		/**
		 * 最后抓取时间
		 */
		private String dateLastCrawled;

		/**
		 * 缓存页面URL 可用于查看网页快照
		 */
		private String cachedPageUrl;

		/**
		 * 内容语言
		 */
		private String language;

		/**
		 * 是否适合全家观看 内容安全标识
		 */
		private Boolean isFamilyFriendly;

		/**
		 * 是否为导航型结果 如官网、主页等
		 */
		private Boolean isNavigational;

	}

	/**
	 * 图片搜索结果类
	 * <p>
	 * 封装图片搜索的结果集
	 */
	@Data
	public static class Images {

		/**
		 * 结果集唯一标识
		 */
		private String id;

		/**
		 * 读取链接
		 */
		private String readLink;

		/**
		 * 图片搜索的完整URL
		 */
		private String webSearchUrl;

		/**
		 * 图片结果数组
		 */
		private Value[] value;

		/**
		 * 内容安全标识
		 */
		private Boolean isFamilyFriendly;

	}

}
