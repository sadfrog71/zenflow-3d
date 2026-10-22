package com.deepdimen.matrix.knowledge.support.flow.model.processor;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.template.Template;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.TemplateUtil;
import com.deepdimen.matrix.knowledge.support.flow.constants.FlowConstant;
import com.deepdimen.matrix.knowledge.support.flow.constants.NodeTypeConstants;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowContextHolder;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowException;
import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiTextNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文本节点处理器 用于处理流程中的文本节点，返回固定文本内容，支持模板变量替换 适用于需要输出静态提示信息、模板消息等场景
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Slf4j
@Component(NodeTypeConstants.TEXT)
public class TextNodeProcessor extends AbstractNodeProcessor {

	/**
	 * 模板引擎，用于处理文本中的变量替换
	 */
	public static final TemplateEngine engine = TemplateUtil.createEngine(new TemplateConfig());

	/**
	 * 执行文本节点的具体逻辑 1. 验证节点配置 2. 获取输入参数 3. 使用模板引擎替换文本中的变量 4. 返回处理后的文本内容
	 * @param node 节点定义
	 * @param context 执行上下文
	 * @return 包含文本内容的字典
	 */
	@Override
	protected Dict doExecute(AiNodeDefinition node, FlowContextHolder context) {
		try {
			// 验证文本节点配置
			AiTextNode config = validateTextConfig(node);

			// 获取输入参数
			Dict variables = getInputVariables(node, context);

			// 处理文本内容，支持模板变量替换
			String processedContent = processTextContent(config.getContent(), variables);

			// 返回处理后的文本内容
			return Dict.create()
				.set(FlowConstant.CONTENT, processedContent)
				.set(FlowConstant.RESULT, processedContent)
				.set(FlowConstant.TIMESTAMP, System.currentTimeMillis());

		}
		catch (Exception e) {
			log.error("文本节点[{}]执行失败", node.getId(), e);
			throw FlowException.nodeError(node.getId(), "[文本节点] -> " + e.getMessage());
		}
	}

	/**
	 * 验证文本节点配置的有效性
	 * @param node 节点定义
	 * @return 有效的文本节点配置
	 * @throws FlowException 如果配置无效则抛出异常
	 */
	private AiTextNode validateTextConfig(AiNodeDefinition node) {
		AiTextNode config = node.getTextParams();
		if (config == null) {
			throw FlowException.invalidParam("文本节点配置无效");
		}

		if (StrUtil.isBlank(config.getContent())) {
			throw FlowException.invalidParam("文本节点内容不能为空");
		}

		return config;
	}

	/**
	 * 处理文本内容，支持模板变量替换
	 * @param content 原始文本内容
	 * @param variables 变量字典
	 * @return 处理后的文本内容
	 */
	private String processTextContent(String content, Dict variables) {
		try {
			// 如果文本中没有模板变量，直接返回原内容
			if (!content.contains("${")) {
				return content;
			}

			// 使用模板引擎进行变量替换
			Template template = engine.getTemplate(content);
			return template.render(variables);
		}
		catch (Exception e) {
			log.warn("文本模板处理失败，返回原始内容: {}", e.getMessage());
			// 如果模板处理失败，返回原始内容
			return content;
		}
	}

}