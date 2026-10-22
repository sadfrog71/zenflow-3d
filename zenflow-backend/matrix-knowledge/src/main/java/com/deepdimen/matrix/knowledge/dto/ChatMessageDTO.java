package com.deepdimen.matrix.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天消息数据传输对象
 * <p>
 * 封装了AI聊天交互中的消息数据，包括消息内容、会话信息、知识库配置等
 *
 * @author caoyuan
 * @date 2024/3/20
 */
@Data
public class ChatMessageDTO implements Serializable {

	/**
	 * Server-only context: neither request JSON nor model tool arguments may supply it.
	 */
	@com.fasterxml.jackson.annotation.JsonIgnore
	@lombok.ToString.Exclude
	private transient com.deepdimen.matrix.knowledge.support.dataquery.DataQueryAiChatService.Context dataQueryContext;

	@com.fasterxml.jackson.annotation.JsonIgnore
	private transient java.util.Map<String, Object> dataQuery;

	/**
	 * 模型名称
	 * <p>
	 * 平台内部的AI模型唯一标识名称
	 */
	private String modelName;

	/**
	 * 消息键值
	 * <p>
	 * 用于标识消息的唯一键值
	 */
	private Long messageKey;

	/**
	 * 会话ID
	 * <p>
	 * 标识一次完整对话的唯一标识符
	 */
	private String conversationId;

	/**
	 * 内部会话标志
	 * <p>
	 * 标识是否为系统内部会话
	 */
	private boolean isInner;

	/**
	 * 访问密钥
	 * <p>
	 * 用于外部访问的认证密钥
	 */
	private String accessKey;

	/**
	 * 历史消息检索数量
	 * <p>
	 * 从知识库检索的历史消息条数，默认为3条
	 */
	private Integer retrieveSize = 3;

	/**
	 * 知识库ID
	 * <p>
	 * 关联的知识库唯一标识符
	 */
	@NotNull
	private Long datasetId;

	/**
	 * 消息内容
	 * <p>
	 * 用户输入的聊天消息文本内容
	 */
	@NotBlank
	private String content;

	/**
	 * 扩展详情
	 * <p>
	 * 包含功能调用、文件上传等扩展信息 示例: { "funcName": "查询", "table": { "dsName": "mysql",
	 * "tableNames": ["user"] } }
	 */
	private ExtDetails extDetails = new ExtDetails();

	/**
	 * 网络搜索开关
	 * <p>
	 * 是否启用网络搜索功能
	 */
	private boolean websearch;

	/**
	 * 智能体ID
	 * <p>
	 * 关联的智能体唯一标识符，不为空时启用智能体配置（含灰度路由）
	 */
	private Long agentId;

	/**
	 * 智能体配置JSON
	 * <p>
	 * 由灰度路由逻辑填充：命中灰度版本时为灰度版本config，否则为当前发布版本config
	 */
	private String agentConfig;

	@Data
	@FieldNameConstants
	public static class ExtDetails implements Serializable {

		/**
		 * 功能名称
		 * <p>
		 * 调用的功能或工具名称
		 */
		private String funcName;

		/**
		 * 数据ID
		 * <p>
		 * 关联的数据记录标识符
		 */
		private Long dataId;

		/**
		 * 访问令牌
		 * <p>
		 * 用户认证的访问令牌
		 */
		private String accessToken;

		/**
		 * 租户ID
		 * <p>
		 * 多租户系统中的租户标识符
		 */
		private Long tenantId;

		/**
		 * 文件列表
		 * <p>
		 * 上传的文件信息列表
		 */
		private List<FileDTO> files = new ArrayList();

		/**
		 * 图像列表
		 * <p>
		 * 上传的图像文件列表
		 */
		private List<FileDTO> images = new ArrayList();

		/**
		 * MCP标识
		 * <p>
		 * Model Context Protocol的标识符
		 */
		private String mcpId;

		/**
		 * 图表类型
		 * <p>
		 * 生成的图表类型（如：柱状图、折线图等）
		 */
		private String chartType;

		/**
		 * 图表ID
		 * <p>
		 * 生成的图表唯一标识符
		 */
		private String chartId;

	}

	@Data
	public static class Table implements Serializable {

		/**
		 * 数据源名称
		 * <p>
		 * 数据库或数据源的名称
		 */
		String dsName;

		/**
		 * 表名数组
		 * <p>
		 * 需要查询的数据表名称列表
		 */
		String[] tableNames;

	}

	/**
	 * 文件数据传输对象
	 * <p>
	 * 封装文件的基本信息
	 *
	 * @author caoyuan
	 * @date 2025/03/20
	 */
	@Data
	public static class FileDTO implements Serializable {

		/**
		 * 文件名称
		 * <p>
		 * 文件的原始名称
		 */
		private String name;

		/**
		 * 文件URL
		 * <p>
		 * 文件的访问地址
		 */
		private String url;

	}

}
