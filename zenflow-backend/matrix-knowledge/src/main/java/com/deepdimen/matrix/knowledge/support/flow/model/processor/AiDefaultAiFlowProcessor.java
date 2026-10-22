package com.deepdimen.matrix.knowledge.support.flow.model.processor;

import cn.hutool.core.lang.Dict;
import com.deepdimen.matrix.knowledge.dto.AiFlowExecuteDTO;
import com.deepdimen.matrix.knowledge.entity.AiFlowEntity;
import com.deepdimen.matrix.knowledge.support.flow.constants.ExecutionStatusEnums;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowContextHolder;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowException;
import com.deepdimen.matrix.knowledge.support.flow.model.AiFlowConnectionDefinition;
import com.deepdimen.matrix.knowledge.support.flow.model.AiFlowDSLDefinition;
import com.deepdimen.matrix.knowledge.support.flow.model.AiFlowExecuteResult;
import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.deepdimen.matrix.knowledge.support.flow.constants.FlowConstant.INDEX;
import static com.deepdimen.matrix.knowledge.support.flow.constants.FlowConstant.TOKENS;
import static com.deepdimen.matrix.knowledge.support.flow.constants.NodeTypeConstants.*;

/**
 * 默认 AI 流处理器 负责执行AI工作流的核心处理器，处理节点的执行顺序和流程控制
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Service
@RequiredArgsConstructor
public class AiDefaultAiFlowProcessor implements AiFlowProcessor {

	/**
	 * 节点处理器工厂，用于获取特定类型的节点处理器
	 */
	private final AiNodeProcessorFactory nodeProcessorFactory;

	/**
	 * 执行工作流 1. 初始化执行上下文 2. 查找开始节点并设置初始参数 3. 使用DFS深度优先搜索执行节点 4. 收集执行结果并返回
	 * @param flow 工作流定义
	 * @param dsl 工作流DSL定义
	 * @param flowExecuteDTO 执行请求参数
	 * @return 执行结果
	 */
	@Override
	public AiFlowExecuteResult execute(AiFlowEntity flow, AiFlowDSLDefinition dsl, AiFlowExecuteDTO flowExecuteDTO) {
		List<AiNodeDefinition> nodes = dsl.getNodes();
		List<AiFlowConnectionDefinition> connections = dsl.getConnections();

		// 创建执行上下文
		FlowContextHolder context = new FlowContextHolder(flow.getId(), flow.getType());
		context.setParameters(flowExecuteDTO.getParams());
		context.setEnvs(flowExecuteDTO.getEnvs());
		context.setAiFlowExecuteDTO(flowExecuteDTO);
		context.setStream(flowExecuteDTO.isStream());
		context.setConversationId(flowExecuteDTO.getConversationId());

		// 获取开始节点
		AiNodeDefinition startNode = nodes.stream()
			.filter(node -> START.equals(node.getType()))
			.findFirst()
			.orElseThrow(() -> FlowException.invalidFlow("未找到开始节点"));

		// 设置开始节点参数
		Optional.ofNullable(startNode.getInputParams())
			.orElseGet(Collections::emptyList)
			.stream()
			.filter(param -> flowExecuteDTO.getParams().containsKey(param.getType()))
			.forEach(param -> context.setVariable(startNode.getId(), param.getType(),
					flowExecuteDTO.getParams().get(param.getType())));

		// 初始化节点执行列表
		List<AiNodeDefinition> executedNodes = new ArrayList<>();
		Set<String> visited = new HashSet<>();

		// 执行节点逻辑
		executeNode(startNode, context, nodes, connections, executedNodes, visited);

		// 创建执行结果
		AiFlowExecuteResult result = new AiFlowExecuteResult();
		result.setNodes(executedNodes);
		result.setVariables(context.getVariables());
		result.setResult(context.getResult());
		result.setDuration(context.getDuration());
		result.setTotalTokens(context.getTotalTokens());
		result.setExecuted(ExecutionStatusEnums.SUCCESS.getValue());
		return result;
	}

	/**
	 * 执行节点（无分支索引）
	 * @param node 节点定义
	 * @param context 执行上下文
	 * @param nodes 所有节点列表
	 * @param connections 连接关系列表
	 * @param executedNodes 已执行节点列表
	 * @param visited 已访问节点集合
	 */
	private void executeNode(AiNodeDefinition node, FlowContextHolder context, List<AiNodeDefinition> nodes,
			List<AiFlowConnectionDefinition> connections, List<AiNodeDefinition> executedNodes, Set<String> visited) {
		executeNode(node, null, context, nodes, connections, executedNodes, visited);
	}

	/**
	 * DFS深度搜索执行节点 递归执行工作流节点，处理分支逻辑和循环检测
	 * @param node 当前节点
	 * @param branchIndex 分支索引（用于条件分支）
	 * @param context 执行上下文
	 * @param nodes 所有节点列表
	 * @param connections 连接关系列表
	 * @param executedNodes 已执行节点列表
	 * @param visited 已访问节点集合
	 */
	private void executeNode(AiNodeDefinition node, Integer branchIndex, FlowContextHolder context,
			List<AiNodeDefinition> nodes, List<AiFlowConnectionDefinition> connections,
			List<AiNodeDefinition> executedNodes, Set<String> visited) {
		String nodeId = node.getId();
		// 循环依赖检测
		if (visited.contains(nodeId)) {
			if (executedNodes.stream().anyMatch(n -> n.getId().equals(nodeId))) {
				throw FlowException.invalidFlow("检测到循环依赖，涉及节点: " + nodeId);
			}
			return;
		}

		// 创建执行节点副本，避免修改原始节点定义
		AiNodeDefinition execNode = new AiNodeDefinition();
		BeanUtils.copyProperties(node, execNode);
		execNode.setStatus(ExecutionStatusEnums.PENDING.getValue());
		execNode.setBranchIndex(branchIndex);

		visited.add(nodeId);
		long startTime = System.currentTimeMillis();

		try {
			execNode.setStatus(ExecutionStatusEnums.RUNNING.getValue());

			// 获取并执行对应类型的节点处理器
			AiNodeProcessor processor = nodeProcessorFactory.getProcessor(node.getType());
			Dict result = processor.execute(execNode, context);

			// 更新执行状态
			execNode.setStatus(ExecutionStatusEnums.SUCCESS.getValue());
			execNode.setOutput(result);
			execNode.setDuration(System.currentTimeMillis() - startTime);

			// 更新Token数量（用于计费统计）
			int tokens = result.get(TOKENS, 0);
			if (tokens > 0) {
				execNode.setTokens(tokens);
				context.addTokens(tokens);
			}

			// 更新返回参数至上下文
			if (!result.isEmpty()) {
				context.setVariables(nodeId, result);
			}

			// 添加到已执行节点列表
			executedNodes.add(execNode);

			// 处理后续节点
			String nodeType = node.getType();
			if (SWITCH.equals(nodeType) || QUESTION.equals(nodeType)) {
				// 处理分支节点：根据条件选择特定分支
				handleBranchNode(nodeId, result, connections, nodes, context, executedNodes, visited);
			}
			else {
				// 处理普通节点：执行所有后续节点
				handleRegularNode(nodeId, connections, nodes, branchIndex, context, executedNodes, visited);
			}

		}
		catch (Exception e) {
			// 错误处理：标记节点执行失败
			execNode.setStatus(ExecutionStatusEnums.ERROR.getValue());
			execNode.setDuration(System.currentTimeMillis() - startTime);
			execNode.setError(e.getMessage());
			executedNodes.add(execNode);
		}

	}

	/**
	 * 处理分支节点的后续执行
	 */
	private void handleBranchNode(String nodeId, Dict result, List<AiFlowConnectionDefinition> connections,
			List<AiNodeDefinition> nodes, FlowContextHolder context, List<AiNodeDefinition> executedNodes,
			Set<String> visited) {
		// 获取分支索引
		Integer branchIdx = result.getInt(INDEX);
		context.setBranchStatus(nodeId, branchIdx);

		// 查找对应分支的连接
		Optional<AiFlowConnectionDefinition> nextConn = connections.stream()
			.filter(conn -> nodeId.equals(conn.getSourceId()) && branchIdx.equals(conn.getPortIndex()))
			.findFirst();

		if (nextConn.isPresent()) {
			// 获取下一个节点
			String nextNodeId = nextConn.get().getTargetId();
			AiNodeDefinition nextNode = nodes.stream()
				.filter(n -> nextNodeId.equals(n.getId()))
				.findFirst()
				.orElseThrow(() -> FlowException.invalidFlow("未找到节点: " + nextNodeId));

			// 执行下一个节点
			executeNode(nextNode, branchIdx, context, nodes, connections, executedNodes, visited);
		}
	}

	/**
	 * 处理普通节点的后续执行
	 */
	private void handleRegularNode(String nodeId, List<AiFlowConnectionDefinition> connections,
			List<AiNodeDefinition> nodes, Integer branchIndex, FlowContextHolder context,
			List<AiNodeDefinition> executedNodes, Set<String> visited) {
		// 获取所有后续连接
		List<AiFlowConnectionDefinition> nextConnection = connections.stream()
			.filter(conn -> nodeId.equals(conn.getSourceId()))
			.sorted(Comparator.comparing(conn -> conn.getPortIndex() != null ? conn.getPortIndex() : 0))
			.toList();

		// 执行所有后续节点
		for (AiFlowConnectionDefinition conn : nextConnection) {
			String nextNodeId = conn.getTargetId();
			AiNodeDefinition nextNode = nodes.stream()
				.filter(n -> nextNodeId.equals(n.getId()))
				.findFirst()
				.orElseThrow(() -> FlowException.invalidFlow("未找到节点: " + nextNodeId));

			executeNode(nextNode, branchIndex, context, nodes, connections, executedNodes, visited);
		}
	}

}
