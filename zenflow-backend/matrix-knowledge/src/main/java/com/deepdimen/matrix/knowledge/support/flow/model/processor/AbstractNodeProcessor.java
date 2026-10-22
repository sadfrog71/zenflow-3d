package com.deepdimen.matrix.knowledge.support.flow.model.processor;

import cn.hutool.core.lang.Dict;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowContextHolder;
import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;
import com.deepdimen.matrix.knowledge.support.flow.model.AiParamDefinition;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 节点处理器抽象基类 为所有流程节点处理器提供通用的执行框架和工具方法
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@RequiredArgsConstructor
public abstract class AbstractNodeProcessor implements AiNodeProcessor {

	/**
	 * 执行节点的模板方法 提供统一的执行流程：参数验证 -> 执行具体逻辑
	 * @param node 节点定义信息
	 * @param context 流程执行上下文
	 * @return 节点执行结果
	 */
	@Override
	public Dict execute(AiNodeDefinition node, FlowContextHolder context) {
		// 验证参数
		validateParams(node, context);
		// 执行节点逻辑
		return doExecute(node, context);
	}

	/**
	 * 执行节点具体逻辑，由子类实现
	 * @param node 节点配置
	 * @param context 上下文
	 * @return 执行结果
	 */
	protected abstract Dict doExecute(AiNodeDefinition node, FlowContextHolder context);

	/**
	 * 验证参数，子类可重写此方法添加特定的验证逻辑
	 * @param node 节点定义
	 * @param context 执行上下文
	 */
	protected void validateParams(AiNodeDefinition node, FlowContextHolder context) {

	}

	/**
	 * 获取输入节点参数值 从上下文中提取节点所需的输入参数
	 * @param node 节点配置
	 * @param context 上下文
	 * @return 包含所有输入参数的字典
	 */
	protected Dict getInputVariables(AiNodeDefinition node, FlowContextHolder context) {
		Dict variables = Dict.create();
		List<AiParamDefinition> inputParams = node.getInputParams();
		if (inputParams == null || inputParams.isEmpty()) {
			return variables;
		}
		inputParams.forEach(param -> variables.set(param.getName(), context.getVariable(param.getType())));
		return variables;
	}

	/**
	 * 获取指定节点的输出参数值 根据节点定义的输出参数类型，从执行结果中提取对应的值
	 * @param node 节点配置信息
	 * @param kv 执行结果的键值对存储
	 * @return 包含节点输出参数值的字典对象
	 */
	protected Dict getOutputVariables(AiNodeDefinition node, Dict kv) {
		Dict variables = Dict.create();
		List<AiParamDefinition> outputParams = node.getOutputParams();
		if (outputParams == null || outputParams.isEmpty()) {
			return variables;
		}
		outputParams.forEach(param -> {
			if (param.getName() != null) {
				String type = param.getType().toLowerCase();
				// 根据参数类型进行类型转换
				switch (type) {
					case "string":
						variables.set(param.getName(), kv.getStr(param.getName()));
						break;
					case "int":
						variables.set(param.getName(), kv.getInt(param.getName()));
						break;
					case "long":
						variables.set(param.getName(), kv.getLong(param.getName()));
						break;
					case "double":
					case "number":
						variables.set(param.getName(), kv.getDouble(param.getName()));
						break;
					case "boolean":
						variables.set(param.getName(), kv.getBool(param.getName()));
						break;
					default:
						variables.set(param.getName(), kv.get(param.getName()));
						break;
				}
			}
		});
		return variables;
	}

	/**
	 * 添加查询参数到URL 用于构建带参数的URL请求
	 * @param url URL地址
	 * @param name 参数名
	 * @param value 参数值
	 * @return 添加参数后的URL
	 */
	protected String addQueryParam(String url, String name, Object value) {
		if (url == null || name == null || value == null) {
			return url;
		}
		String separator = url.contains("?") ? "&" : "?";
		return url + separator + name + "=" + value;
	}

}
