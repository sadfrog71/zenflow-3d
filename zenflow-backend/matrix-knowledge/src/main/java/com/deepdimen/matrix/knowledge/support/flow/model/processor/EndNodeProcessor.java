package com.deepdimen.matrix.knowledge.support.flow.model.processor;

import cn.hutool.core.lang.Dict;
import com.deepdimen.matrix.knowledge.dto.AiFlowExecuteDTO;
import com.deepdimen.matrix.knowledge.support.flow.constants.ExecutionStatusEnums;
import com.deepdimen.matrix.knowledge.support.flow.constants.FlowConstant;
import com.deepdimen.matrix.knowledge.support.flow.constants.NodeTypeConstants;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowContextHolder;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowException;
import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.deepdimen.matrix.knowledge.support.constant.AiChatConstants.END_MSG;

/**
 * 结束节点处理器 用于处理流程的结束节点，收集最终的输出参数，并支持流式返回
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Slf4j
@Component(NodeTypeConstants.END)
public class EndNodeProcessor extends AbstractNodeProcessor {

	/**
	 * 执行结束节点的具体逻辑 1. 获取所有输入参数作为最终结果 2. 将结果保存到上下文中 3. 如果是流式模式，发送结束回调 4. 返回结果字典
	 * @param node 节点定义
	 * @param context 执行上下文
	 * @return 包含最终结果的字典
	 */
	@Override
	protected Dict doExecute(AiNodeDefinition node, FlowContextHolder context) {
		try {
			// 获取最终输入参数，这些参数将作为整个流程的输出
			Dict variables = getInputVariables(node, context);

			// 将结果保存到上下文中，供流程框架获取
			context.setVariable(FlowConstant.RESULT, variables);

			// 如果是流式模式且有回调，发送流程结束信号
			if (context.isStream() && context.getAiFlowExecuteDTO() != null
					&& context.getAiFlowExecuteDTO().getCallback() != null) {

				// 构建结束回调数据
				AiFlowExecuteDTO.FlowCallbackData.FlowCallbackDataBuilder callbackDataBuilder = AiFlowExecuteDTO.FlowCallbackData
					.builder()
					.content(END_MSG)
					.isEnd(true)
					.duration(context.getDuration())
					.nodeType(node.getType())
					.nodeId(node.getId());

				// 设置节点执行状态
				node.setStatus(ExecutionStatusEnums.SUCCESS.getValue());
				node.setDuration(context.getDuration());
				context.getExecutedNodes().add(node);
				callbackDataBuilder.nodes(context.getExecutedNodes());

				// 执行流式回调
				context.getAiFlowExecuteDTO()
					.getCallback()
					.execute(AiFlowExecuteDTO.FlowCallbackResult.builder().data(callbackDataBuilder.build()).build());
			}

			return Dict.create()
				.set(FlowConstant.RESULT, variables)
				.set(FlowConstant.TIMESTAMP, System.currentTimeMillis());
		}
		catch (Exception e) {
			log.error("结束节点[{}]执行失败", node.getId(), e);
			throw FlowException.nodeError(node.getId(), "[结束节点] -> " + e.getMessage());
		}
	}

}
