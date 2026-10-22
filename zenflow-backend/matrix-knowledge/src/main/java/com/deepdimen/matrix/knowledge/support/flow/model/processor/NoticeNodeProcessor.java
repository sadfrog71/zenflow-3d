package com.deepdimen.matrix.knowledge.support.flow.model.processor;

import cn.hutool.core.lang.Dict;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.TemplateUtil;
import com.deepdimen.matrix.admin.api.dto.MessageHookDTO;
import com.deepdimen.matrix.admin.api.feign.RemoteMessageService;
import com.deepdimen.matrix.knowledge.support.flow.constants.FlowConstant;
import com.deepdimen.matrix.knowledge.support.flow.constants.NodeTypeConstants;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowContextHolder;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowException;
import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiNoticeNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 消息节点处理器
 */
@Slf4j
@Component(NodeTypeConstants.NOTICE)
@RequiredArgsConstructor
public class NoticeNodeProcessor extends AbstractNodeProcessor {

	public static final TemplateEngine engine = TemplateUtil.createEngine(new TemplateConfig());

	private final RemoteMessageService remoteMessageService;

	@Override
	protected Dict doExecute(AiNodeDefinition node, FlowContextHolder context) {
		try {
			// 节点配置判断
			AiNoticeNode config = node.getNoticeParams();
			if (config == null || config.getTemplateCode() == null) {
				throw FlowException.invalidParam("消息节点配置无效");
			}
			// 获取输入参数
			Dict variables = getInputVariables(node, context);

			MessageHookDTO messageHookDTO = new MessageHookDTO();
			messageHookDTO.setBizCode(config.getTemplateId());

			String render = engine.getTemplate(config.getTemplateCode()).render(variables);
			messageHookDTO.setMessageContent(render);
			messageHookDTO.setMessageType("text");
			remoteMessageService.sendHook(messageHookDTO);
			// 设置结果
			return Dict.create().set(FlowConstant.RESULT, true).set(FlowConstant.TIMESTAMP, System.currentTimeMillis());
		}
		catch (Exception e) {
			log.error("消息节点[{}]执行失败", node.getId(), e);
			throw FlowException.nodeError(node.getId(), "[消息节点] -> " + e.getMessage());
		}
	}

}
