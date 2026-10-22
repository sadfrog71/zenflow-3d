package com.deepdimen.matrix.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AI消息结果数据传输对象
 * <p>
 * 用于封装AI模型返回的消息内容、推理过程、相关资源链接等信息
 *
 * @author caoyuan
 * @date 2024/5/28
 */
@Data
public class AiMessageResultDTO {

	/** Metadata-only explanation, not controlled SQL execution evidence. */
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	private java.util.Map<String, Object> metadataGuidance;

	/** Optional structured result from the opt-in controlled database chat path. */
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	private java.util.Map<String, Object> dataQuery;

	private java.util.Map<String, Object> queryProgress;

	/** 消息键 */
	String messageKey;

	/**
	 * 返回消息的内容
	 * <p>
	 * AI模型生成的主要文本回复内容
	 */
	String message;

	/**
	 * 推理内容
	 * <p>
	 * AI模型的推理过程或思考链路的详细记录
	 */
	String reasoningContent;

	/**
	 * 相关资源链接列表
	 * <p>
	 * 与回答相关的外部资源或参考文档链接
	 */
	List<ExtLink> extLinks = new ArrayList<>();

	/** 工具信息 */
	private ToolInfo toolInfo;

	/**
	 * 文件路径
	 * <p>
	 * 生成的文件或资源的存储路径
	 */
	String path;

	/**
	 * 图表ID
	 * <p>
	 * 生成的图表的唯一标识符
	 */
	String chartId;

	/**
	 * 图表类型
	 * <p>
	 * 生成的图表的类型（如：柱状图、折线图、饼图等）
	 */
	String chartType;

	/**
	 * 消息流是否结束标志
	 * <p>
	 * true表示消息流已结束，false表示还有后续消息
	 */
	boolean finish;

	/** 确认表单 */
	String confirmForm;

	/** 功能名称 */
	String functionName;

	/**
	 * 无参构造方法
	 */
	public AiMessageResultDTO() {
	}

	/**
	 * 构造方法，创建包含指定消息的AiMessageResultDTO对象
	 * @param message 消息内容
	 */
	public AiMessageResultDTO(String message) {
		this.message = message;
	}

	/**
	 * 构造方法，创建AiMessageResultDTO对象
	 * @param message 消息内容
	 * @param path 路径信息
	 */
	public AiMessageResultDTO(String message, String path) {
		this.message = message;
		this.path = path;
	}

	/**
	 * 构造方法，创建AiMessageResultDTO对象
	 * @param message 消息内容
	 * @param extLinks 外部链接列表
	 */
	public AiMessageResultDTO(String message, List<ExtLink> extLinks) {
		this.message = message;
		this.extLinks = extLinks;
	}

	/**
	 * 扩展链接内部类
	 * <p>
	 * 用于封装外部资源的详细信息，包括名称、URL和相关性距离
	 */
	@Data
	@AllArgsConstructor
	public static class ExtLink {

		/**
		 * 资源名称
		 * <p>
		 * 外部资源的显示名称或标题
		 */
		private String name;

		/**
		 * 资源链接
		 * <p>
		 * 外部资源的完整URL地址
		 */
		private String url;

		/**
		 * 相关性距离
		 * <p>
		 * 表示该资源与查询内容的相关性程度，值越小相关性越高
		 */
		private Float distance;

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof ExtLink extLink))
				return false;
			return Objects.equals(url, extLink.url);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(url);
		}

	}

	/**
	 * 工具信息内部类
	 * <p>
	 * 用于封装AI模型使用的工具调用信息
	 */
	@Data
	public static class ToolInfo {

		/**
		 * 工具名称
		 * <p>
		 * AI模型调用的工具名称
		 */
		private String name;

		/**
		 * 工具调用结果
		 * <p>
		 * 工具执行后的返回结果
		 */
		private String params;

	}

}
