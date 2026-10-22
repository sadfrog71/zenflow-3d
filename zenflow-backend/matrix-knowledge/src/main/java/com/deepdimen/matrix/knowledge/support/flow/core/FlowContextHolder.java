package com.deepdimen.matrix.knowledge.support.flow.core;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.deepdimen.matrix.knowledge.support.constant.AiChatConstants;
import com.deepdimen.matrix.knowledge.support.flow.constants.ExecutionStatusEnums;
import com.deepdimen.matrix.knowledge.dto.AiFlowExecuteDTO;
import com.deepdimen.matrix.knowledge.support.flow.constants.FlowConstant;
import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI工作流执行上下文持有者 用于在整个流程执行过程中维护和传递上下文信息 包括变量存储、参数传递、节点执行状态跟踪等功能
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Data
@FieldNameConstants
public class FlowContextHolder {

	/**
	 * 工作流ID - 当前执行的流程唯一标识
	 */
	private final Long flowId;

	/**
	 * 工作流类型 - 标识流程的业务类型
	 */
	private final String type;

	/**
	 * 会话ID - 用于标识一次完整的对话或执行会话
	 */
	private String conversationId;

	/**
	 * 是否正在流式传输 - 标记是否采用流式输出模式
	 */
	private boolean stream;

	/**
	 * 环境变量 - 存储流程级别的全局配置和环境信息
	 */
	private Map<String, Object> envs;

	/**
	 * 变量映射 - 存储每个节点执行完毕后的返回值集合 格式：nodeId.variableName -> value
	 */
	private final Map<String, Object> variables;

	/**
	 * 参数映射 - 调用接口时传递的完整参数集合 用于存储流程启动时的输入参数
	 */
	private final Map<String, Object> parameters;

	/**
	 * 分支状态 - 记录各个分支节点的激活状态 用于switch节点记录选择了哪个分支
	 */
	private final Map<String, Integer> activeBranches;

	/**
	 * AI流程执行数据传输对象 - 包含流程执行的详细信息
	 */
	private AiFlowExecuteDTO aiFlowExecuteDTO;

	/**
	 * 已执行的节点列表 - 按执行顺序记录所有已处理的节点
	 */
	private List<AiNodeDefinition> executedNodes = new ArrayList<>();

	/**
	 * Token使用量 - 累计消耗的Token总数
	 */
	private long totalTokens;

	/**
	 * 开始时间 - 流程开始执行的时间戳
	 */
	private final long startTime;

	/**
	 * 构造函数 - 初始化流程上下文
	 * @param flowId 流程ID
	 * @param type 流程类型
	 */
	public FlowContextHolder(Long flowId, String type) {
		this.flowId = flowId;
		this.type = type;
		this.variables = new HashMap<>();
		this.parameters = new HashMap<>();
		this.activeBranches = new HashMap<>();
		this.startTime = System.currentTimeMillis();
	}

	/**
	 * 获取节点变量名 - 生成带节点ID前缀的完整变量名
	 * @param nodeId 节点ID
	 * @param key 变量名
	 * @return 变量名(格式：nodeId.key)
	 */
	public String getNodeKey(String nodeId, String key) {
		return nodeId + StringPool.DOT + key;
	}

	/**
	 * 获取变量值 - 支持多种来源的变量获取
	 * @param key 变量名(可包含节点ID前缀)
	 * @return 变量值，按优先级从variables、parameters、envs中查找
	 */
	public Object getVariable(String key) {
		Object obj = variables.get(key);
		// 如果变量值为空且变量名不包含节点ID前缀，则直接返回变量名本身(用户手动输入固定数值的情况)
		if (obj == null && !StrUtil.containsAnyIgnoreCase(key, StringPool.DOT)) {
			return key;
		}

		if (obj == null) {
			return parameters.get(key) == null ? envs.get(key) : parameters.get(key);
		}
		return obj;
	}

	/**
	 * 获取变量值 - 根据节点ID和变量名获取
	 * @param nodeId 节点ID
	 * @param key 变量名
	 * @return 变量值
	 */
	public Object getVariable(String nodeId, String key) {
		return variables.get(getNodeKey(nodeId, key));
	}

	/**
	 * 设置变量值 - 直接设置完整的变量名和值
	 * @param key 变量名
	 * @param value 变量值
	 */
	public void setVariable(String key, Object value) {
		variables.put(key, value);
	}

	/**
	 * 设置变量值 - 根据节点ID设置变量
	 * @param nodeId 节点ID
	 * @param key 变量名
	 * @param value 变量值
	 */
	public void setVariable(String nodeId, String key, Object value) {
		variables.put(getNodeKey(nodeId, key), value);
	}

	/**
	 * 批量设置变量值 - 一次性设置多个变量
	 * @param nodeId 节点ID
	 * @param kv 变量集合
	 */
	public void setVariables(String nodeId, Dict kv) {
		kv.forEach((k, v) -> variables.put(getNodeKey(nodeId, k), v));
	}

	/**
	 * 获取参数值 - 获取流程启动参数
	 * @param key 参数名
	 * @return 参数值
	 */
	public Object getParameter(String key) {
		return parameters.get(key);
	}

	/**
	 * 设置参数值 - 设置单个参数
	 * @param key 参数名
	 * @param value 参数值
	 */
	public void setParameter(String key, Object value) {
		parameters.put(key, value);
	}

	/**
	 * 批量设置参数值 - 一次性设置多个参数
	 * @param kv 参数集合
	 */
	public void setParameters(Dict kv) {
		parameters.putAll(kv);
	}

	/**
	 * 获取分支状态 - 获取指定节点的分支选择
	 * @param nodeId 节点ID
	 * @return 分支索引
	 */
	public Integer getBranchStatus(String nodeId) {
		return activeBranches.get(nodeId);
	}

	/**
	 * 设置分支状态 - 记录节点选择的分支
	 * @param nodeId 节点ID
	 * @param status 分支索引
	 */
	public void setBranchStatus(String nodeId, Integer status) {
		activeBranches.put(nodeId, status);
	}

	/**
	 * 增加Token使用量 - 累加Token消耗
	 * @param tokens Token数量
	 */
	public void addTokens(long tokens) {
		this.totalTokens += tokens;
	}

	/**
	 * 获取执行时长 - 计算从开始到当前的执行时间
	 * @return 执行时长(毫秒)
	 */
	public long getDuration() {
		return System.currentTimeMillis() - startTime;
	}

	/**
	 * 获取执行结果 - 获取流程的最终执行结果
	 * @return 执行结果对象
	 */
	public Object getResult() {
		return variables.get(FlowConstant.RESULT);
	}

	/**
	 * 发送流式内容片段
	 * <p>
	 * 在流式模式下，向客户端推送实时生成的内容片段 用于LLM、RAG等节点的流式输出场景
	 * @param content 内容片段
	 */
	public void sendStreamContent(String content) {
		if (!stream || aiFlowExecuteDTO == null || aiFlowExecuteDTO.getCallback() == null) {
			return;
		}

		aiFlowExecuteDTO.getCallback()
			.execute(AiFlowExecuteDTO.FlowCallbackResult.builder()
				.data(AiFlowExecuteDTO.FlowCallbackData.builder().content(content).build())
				.build());
	}

	/**
	 * 发送流式结束信号
	 * <p>
	 * 流式处理完成时调用，自动处理节点状态更新、token统计等 简化节点处理器的代码，统一状态管理逻辑
	 * @param node 当前执行的节点（可为null，用于非节点场景）
	 * @param tokens Token使用量（可为null）
	 */
	public void sendStreamEnd(AiNodeDefinition node, Integer tokens) {
		if (!stream || aiFlowExecuteDTO == null || aiFlowExecuteDTO.getCallback() == null) {
			return;
		}

		AiFlowExecuteDTO.FlowCallbackData.FlowCallbackDataBuilder builder = AiFlowExecuteDTO.FlowCallbackData.builder()
			.content(AiChatConstants.END_MSG);

		// 设置token和耗时信息
		if (tokens != null && tokens > 0) {
			builder.tokens(tokens).duration(getDuration());
		}

		// 更新节点状态
		if (node != null) {
			node.setStatus(ExecutionStatusEnums.SUCCESS.getValue());
			node.setDuration(getDuration());
			if (tokens != null && tokens > 0) {
				node.setTokens(tokens);
			}
			executedNodes.add(node);
			builder.nodes(executedNodes);
		}

		aiFlowExecuteDTO.getCallback()
			.execute(AiFlowExecuteDTO.FlowCallbackResult.builder().data(builder.build()).build());
	}

	/**
	 * 发送流式错误信息
	 * <p>
	 * 流式处理出现错误时调用，向客户端推送错误信息并标记流程结束 统一错误处理逻辑，简化节点处理器代码
	 * @param error 错误信息
	 */
	public void sendStreamError(String error) {
		if (!stream || aiFlowExecuteDTO == null || aiFlowExecuteDTO.getCallback() == null) {
			return;
		}

		aiFlowExecuteDTO.getCallback()
			.execute(AiFlowExecuteDTO.FlowCallbackResult.builder()
				.data(AiFlowExecuteDTO.FlowCallbackData.builder().isEnd(true).content("错误：" + error).build())
				.build());
	}

}
