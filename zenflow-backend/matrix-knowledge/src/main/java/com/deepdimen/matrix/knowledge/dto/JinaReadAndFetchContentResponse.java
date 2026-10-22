package com.deepdimen.matrix.knowledge.dto;

import lombok.*;

/**
 * Jina阅读和获取内容响应对象
 * <p>
 * 封装Jina AI服务解析网页内容后的响应数据结构
 *
 * @author caoyuan
 * @date 2025/04/30
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JinaReadAndFetchContentResponse {

	/**
	 * 响应代码
	 * <p>
	 * API调用的响应状态代码
	 */
	private int code;

	/**
	 * 状态码
	 * <p>
	 * HTTP请求的状态码
	 */
	private int status;

	/**
	 * 响应数据
	 * <p>
	 * 包含解析后的网页内容和相关信息
	 */
	private Data data;

	/**
	 * 数据内容类
	 * <p>
	 * 封装解析后的网页内容详情
	 *
	 * @author caoyuan
	 * @date 2025/04/30
	 */
	@Getter
	@Setter
	@AllArgsConstructor
	@NoArgsConstructor
	@Builder
	public static class Data {

		/**
		 * 页面标题
		 * <p>
		 * 网页的标题信息
		 */
		private String title;

		/**
		 * 页面描述
		 * <p>
		 * 网页的描述或摘要信息
		 */
		private String description;

		/**
		 * 页面URL
		 * <p>
		 * 解析的网页地址
		 */
		private String url;

		/**
		 * 主体内容
		 * <p>
		 * 解析后的网页主体文本内容
		 */
		private String content;

		/**
		 * 警告信息
		 * <p>
		 * 解析过程中的警告或提示信息
		 */
		private String warning;

		/**
		 * 使用量信息
		 * <p>
		 * API调用的使用量统计信息
		 */
		private Usage usage;

	}

	/**
	 * 使用量类
	 * <p>
	 * 封装API调用的资源使用情况
	 *
	 * @author caoyuan
	 * @date 2025/04/30
	 */
	@Getter
	@Setter
	@AllArgsConstructor
	@NoArgsConstructor
	@Builder
	public static class Usage {

		/**
		 * 令牌消耗量
		 * <p>
		 * 本次API调用消耗的令牌数量
		 */
		private int tokens;

	}

}
