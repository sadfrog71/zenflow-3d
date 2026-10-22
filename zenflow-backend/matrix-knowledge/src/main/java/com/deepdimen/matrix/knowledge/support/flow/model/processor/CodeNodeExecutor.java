package com.deepdimen.matrix.knowledge.support.flow.model.processor;

import cn.hutool.core.lang.Dict;
import cn.hutool.extra.template.Template;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.TemplateUtil;
import cn.hutool.script.JavaScriptEngine;
import cn.hutool.script.ScriptUtil;
import com.deepdimen.matrix.knowledge.support.flow.constants.FlowConstant;
import com.deepdimen.matrix.knowledge.support.flow.constants.NodeTypeConstants;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowContextHolder;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowException;
import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiCodeNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 代码节点执行器 用于执行JavaScript代码的节点处理器 支持在代码中使用模板变量，并执行main函数
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Slf4j
@Component(NodeTypeConstants.CODE)
public class CodeNodeExecutor extends AbstractNodeProcessor {

	/**
	 * 模板引擎，用于处理代码中的变量替换
	 */
	public static final TemplateEngine engine = TemplateUtil.createEngine(new TemplateConfig());

	/**
	 * JavaScript引擎，用于执行用户编写的JavaScript代码
	 */
	public static final JavaScriptEngine javaScriptEngine = ScriptUtil.getJavaScriptEngine();

	/**
	 * 执行代码节点的具体逻辑 1. 验证节点配置 2. 获取输入参数和环境变量 3. 使用模板引擎替换代码中的变量 4. 执行JavaScript代码 5. 返回执行结果
	 * @param node 节点定义
	 * @param context 执行上下文
	 * @return 包含执行结果的字典
	 */
	@Override
	protected Dict doExecute(AiNodeDefinition node, FlowContextHolder context) {
		try {
			// 节点配置判断
			AiCodeNode config = node.getCodeParams();
			if (config == null || config.getCode() == null) {
				throw FlowException.invalidParam("代码节点配置无效");
			}
			// 获取输入参数
			Dict variables = getInputVariables(node, context);
			// 增加环境变量参数，使代码可以访问环境变量
			variables.putAll(context.getEnvs());
			// 使用模板引擎替换代码中的变量占位符
			Template template = engine.getTemplate(config.getCode());
			String script = template.render(variables);
			// 执行JavaScript代码
			javaScriptEngine.eval(script);

			// 调用代码中的main函数并获取返回值
			String result = javaScriptEngine.invokeFunction("main", variables).toString();
			return Dict.create()
				.set(FlowConstant.RESULT, result)
				.set(FlowConstant.TIMESTAMP, System.currentTimeMillis());
		}
		catch (Exception e) {
			log.error("代码节点[{}]执行失败", node.getId(), e);
			throw FlowException.nodeError(node.getId(), "[代码节点] -> " + e.getMessage());
		}
	}

}
