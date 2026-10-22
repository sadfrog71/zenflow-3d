package com.deepdimen.matrix.knowledge.support.flow.model;

import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiCodeNode;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiDbNode;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiDocumentParserNode;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiHttpNode;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiLLMNode;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiLoopNode;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiMCPNode;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiNoticeNode;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiOcrNode;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiQuestionNode;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiRagNode;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiStructuredNode;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiSwitchNode;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiTextNode;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.*;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI节点定义 流程中单个处理节点的完整定义 包含节点的基本信息、配置参数、执行结果等
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Data
public class AiNodeDefinition {

	/**
	 * 节点ID - 节点的唯一标识符 在整个流程中必须保持唯一
	 */
	private String id;

	/**
	 * 节点类型 - 标识节点的功能类型 如：start、end、llm、http、db等
	 */
	private String type;

	/**
	 * 节点名称 - 节点的显示名称 用于UI展示和日志记录
	 */
	private String name;

	/**
	 * 节点描述 - 节点功能的详细说明 帮助理解节点的作用和用途
	 */
	private String description;

	/**
	 * 节点状态 - 节点的执行状态 如：pending、running、success、error、skipped
	 */
	private String status;

	/**
	 * 节点执行时长 - 节点执行耗时（毫秒） 用于性能分析和优化
	 */
	private Long duration;

	/**
	 * Token使用量 - LLM节点消耗的Token数 仅对LLM类型节点有效
	 */
	private Integer tokens;

	/**
	 * 错误信息 - 节点执行失败时的错误详情 仅在执行出错时有值
	 */
	private String error;

	/**
	 * 输出结果 - 节点执行后的输出数据 键值对形式存储，可被后续节点引用
	 */
	private Map<String, Object> output;

	/**
	 * 分支索引 - 在分支节点中选择的分支序号 用于switch节点记录选择了哪个分支
	 */
	private Integer branchIndex;

	/**
	 * 输入参数配置 - 定义节点需要的输入参数 包含参数名称、类型、是否必填等信息
	 */
	private List<AiParamDefinition> inputParams;

	/**
	 * 输出参数配置 - 定义节点产生的输出参数 描述节点会输出哪些变量供后续使用
	 */
	private List<AiParamDefinition> outputParams;

	/**
	 * HTTP节点参数配置 - HTTP请求相关配置 包含URL、方法、请求头、请求体等
	 */
	private AiHttpNode httpParams;

	/**
	 * 数据库节点参数配置 - 数据库操作相关配置 包含数据源、SQL语句、参数映射等
	 */
	private AiDbNode dbParams;

	/**
	 * RAG节点参数配置 - 知识库检索相关配置 包含知识库ID、检索策略、相似度阈值等
	 */
	private AiRagNode ragParams;

	/**
	 * 分支节点参数配置 - 条件分支相关配置 包含分支条件、默认分支等
	 */
	private AiSwitchNode switchParams;

	/**
	 * 代码节点参数配置 - 自定义代码执行配置 包含代码内容、运行环境等
	 */
	private AiCodeNode codeParams;

	/**
	 * 大模型节点参数配置 - LLM调用相关配置 包含模型选择、提示词、温度参数等
	 */
	private AiLLMNode llmParams;

	/**
	 * 问答节点参数配置 - 用户交互相关配置 包含问题内容、选项列表等
	 */
	private AiQuestionNode questionParams;

	/**
	 * 消息节点参数配置 - 通知发送相关配置 包含通知渠道、接收人、消息模板等
	 */
	private AiNoticeNode noticeParams;

	/**
	 * MCP节点参数配置 - MCP服务调用配置 包含服务地址、方法名、参数等
	 */
	private AiMCPNode mcpParams;

	/**
	 * 文本节点参数配置 - 文本内容输出配置 包含要返回的文本内容，支持模板变量替换
	 */
	private AiTextNode textParams;

	/**
	 * 结构化输出节点参数配置 - JSON生成相关配置 包含JSON Schema定义、模型选择、验证规则等
	 */
	private AiStructuredNode structuredParams;

	/**
	 * 循环节点参数配置 - 循环执行相关配置 包含最大循环次数、子节点列表、子节点连接关系等
	 */
	private AiLoopNode loopParams;

	/**
	 * OCR识别节点参数配置 - 图像文字识别相关配置 包含OCR模板ID、图片URL、识别提示词等
	 */
	private AiOcrNode ocrParams;

	/**
	 * 文档解析节点参数配置 - Office和PDF文档解析相关配置 包含文档URL、解析策略、视觉模型选择等
	 */
	private AiDocumentParserNode documentParserParams;

}
