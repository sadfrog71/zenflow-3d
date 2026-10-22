package com.deepdimen.matrix.knowledge.support.flow.model.processor;

import cn.hutool.core.lang.Dict;
import com.deepdimen.matrix.knowledge.support.flow.constants.FlowConstant;
import com.deepdimen.matrix.knowledge.support.flow.constants.NodeTypeConstants;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowContextHolder;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowException;
import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiLoopNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 循环节点执行器 用于重复执行子流程的节点处理器 支持设置最大循环次数，子流程中可访问当前迭代索引
 *
 * @author caoyuan
 * @date 2025/01/24
 */
@Slf4j
@Component(NodeTypeConstants.LOOP)
@RequiredArgsConstructor
public class LoopNodeExecutor extends AbstractNodeProcessor {

	/**
	 * 节点处理器工厂，用于获取子节点的处理器
	 */
	@Lazy
	private final AiNodeProcessorFactory nodeProcessorFactory;

	/**
	 * 执行循环节点的具体逻辑 1. 验证循环节点配置 2. 循环执行子流程 3. 收集每次迭代的结果 4. 返回结果数组和迭代次数
	 * @param node 节点定义
	 * @param context 执行上下文
	 * @return 包含所有迭代结果的字典
	 */
	@Override
	protected Dict doExecute(AiNodeDefinition node, FlowContextHolder context) {
		try {
			// 节点配置判断
			AiLoopNode config = node.getLoopParams();
			if (config == null || config.getMaxIterations() == null || config.getMaxIterations() <= 0) {
				throw FlowException.invalidParam("循环节点配置无效");
			}

			int maxIterations = config.getMaxIterations();
			List<AiNodeDefinition> childNodes = config.getChildNodes();
			String iterationVarName = config.getIterationVarName() != null ? config.getIterationVarName() : "index";

			// 如果没有子节点，直接返回空结果
			if (childNodes == null || childNodes.isEmpty()) {
				log.warn("循环节点[{}]没有配置子节点", node.getId());
				return Dict.create()
					.set("results", new ArrayList<>())
					.set("iterations", 0)
					.set(FlowConstant.TIMESTAMP, System.currentTimeMillis());
			}

			// 收集每次迭代的结果
			List<Object> results = new ArrayList<>();

			// 循环执行子流程
			for (int i = 0; i < maxIterations; i++) {
				log.debug("循环节点[{}]开始第{}次迭代", node.getId(), i + 1);

				// 为当前迭代创建子上下文
				// 注入迭代变量 loop.index
				context.setVariable("loop", iterationVarName, i);

				// 执行子节点
				Dict iterationResult = executeChildNodes(childNodes, context, i);

				// 收集结果
				results.add(iterationResult);

				log.info("循环节点[{}]第{}次迭代完成", node.getId(), i + 1);
			}

			// 返回所有迭代结果
			return Dict.create()
				.set("results", results)
				.set("iterations", maxIterations)
				.set(FlowConstant.TIMESTAMP, System.currentTimeMillis());
		}
		catch (Exception e) {
			log.error("循环节点[{}]执行失败", node.getId(), e);
			throw FlowException.nodeError(node.getId(), "[循环节点] -> " + e.getMessage());
		}
	}

	/**
	 * 执行子节点序列 按顺序执行所有子节点，并返回最后一个节点的输出
	 * @param childNodes 子节点列表
	 * @param context 执行上下文
	 * @param iteration 当前迭代索引
	 * @return 子流程执行结果
	 */
	private Dict executeChildNodes(List<AiNodeDefinition> childNodes, FlowContextHolder context, int iteration) {
		Dict lastResult = Dict.create();

		for (AiNodeDefinition childNode : childNodes) {
			try {
				// 跳过 start 和 end 节点
				if ("start".equals(childNode.getType()) || "end".equals(childNode.getType())) {
					continue;
				}

				log.debug("循环迭代[{}]执行子节点[{}]", iteration, childNode.getId());

				// 获取子节点对应的处理器
				AiNodeProcessor processor = nodeProcessorFactory.getProcessor(childNode.getType());

				// 执行子节点
				Dict result = processor.execute(childNode, context);

				// 更新上下文变量
				if (result != null && !result.isEmpty()) {
					context.setVariables(childNode.getId(), result);
					lastResult = result;
				}
			}
			catch (Exception e) {
				log.error("循环迭代[{}]执行子节点[{}]失败", iteration, childNode.getId(), e);
				throw new RuntimeException("子节点执行失败: " + e.getMessage(), e);
			}
		}

		return lastResult;
	}

}
