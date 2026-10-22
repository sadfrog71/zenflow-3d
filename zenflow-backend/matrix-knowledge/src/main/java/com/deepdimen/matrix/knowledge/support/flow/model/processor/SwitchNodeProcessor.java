package com.deepdimen.matrix.knowledge.support.flow.model.processor;

import cn.hutool.core.lang.Dict;
import cn.hutool.extra.template.Template;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.TemplateUtil;
import cn.hutool.script.JavaScriptEngine;
import cn.hutool.script.ScriptUtil;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.deepdimen.matrix.knowledge.support.flow.constants.FlowConstant;
import com.deepdimen.matrix.knowledge.support.flow.constants.NodeTypeConstants;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowContextHolder;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowException;
import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiSwitchNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * switch 节点执行程序
 *
 * @author caoyuan
 * @date 2025/03/04
 */
@Slf4j
@Component(NodeTypeConstants.SWITCH)
public class SwitchNodeProcessor extends AbstractNodeProcessor {

	public static final TemplateEngine engine = TemplateUtil.createEngine(new TemplateConfig());

	public static final JavaScriptEngine javaScriptEngine = ScriptUtil.getJavaScriptEngine();

	@Override
	protected Dict doExecute(AiNodeDefinition node, FlowContextHolder context) {
		try {
			// 节点配置判断
			AiSwitchNode config = node.getSwitchParams();
			if (config == null || config.getCases() == null) {
				throw FlowException.invalidParam("分支节点配置无效");
			}
			// 获取输入参数
			Dict variables = getInputVariables(node, context);
			// 替换代码中的变量
			Template template = engine.getTemplate(config.getCode());
			String code = template.render(variables);
			javaScriptEngine.eval(code);

			String result = javaScriptEngine.invokeFunction("main", variables).toString();

			// 执行JavaScript代码并获取结果
			if (StringUtil.isBlank(result)) {
				throw FlowException.invalidParam("未找到变量: " + config.getVariable());
			}
			// 查找匹配的分支
			int caseIndex = getCaseIndex(config, result);
			// 设置结果
			return Dict.create()
				.set(FlowConstant.INDEX, caseIndex)
				.set(FlowConstant.TIMESTAMP, System.currentTimeMillis());
		}
		catch (Exception e) {
			log.error("分支节点[{}]执行失败", node.getId(), e);
			throw FlowException.nodeError(node.getId(), "[分支节点] -> " + e.getMessage());
		}
	}

	/**
	 * 获取匹配的分支索引
	 */
	private static int getCaseIndex(AiSwitchNode config, String result) {
		int caseIndex = -1;
		if (config.getCases() != null && !config.getCases().isEmpty()) {
			for (int i = 0; i < config.getCases().size(); i++) {
				AiSwitchNode.Case caseItem = config.getCases().get(i);
				String caseValue = caseItem.getValue();
				if (StringUtil.equalsIgnoreCase(result, caseValue)) {
					caseIndex = i;
					break;
				}
			}
		}
		return caseIndex;
	}

}
