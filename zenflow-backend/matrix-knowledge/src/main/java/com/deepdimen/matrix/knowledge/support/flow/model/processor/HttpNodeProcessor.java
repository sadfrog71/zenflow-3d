package com.deepdimen.matrix.knowledge.support.flow.model.processor;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.template.Template;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.TemplateUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.Method;
import com.deepdimen.matrix.knowledge.support.flow.constants.FlowConstant;
import com.deepdimen.matrix.knowledge.support.flow.constants.NodeTypeConstants;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowContextHolder;
import com.deepdimen.matrix.knowledge.support.flow.core.FlowException;
import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;
import com.deepdimen.matrix.knowledge.support.flow.model.AiParamDefinition;
import com.deepdimen.matrix.knowledge.support.flow.model.nodes.AiHttpNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP节点处理器 用于在流程中执行HTTP请求，支持GET、POST、PUT、DELETE等方法 可以处理请求头、请求参数、请求体等各种HTTP请求组件
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Slf4j
@Component(NodeTypeConstants.HTTP)
@RequiredArgsConstructor
public class HttpNodeProcessor extends AbstractNodeProcessor {

	/**
	 * 模板引擎，用于处理JSON请求体中的模板变量
	 */
	public static final TemplateEngine engine = TemplateUtil.createEngine(new TemplateConfig());

	@Override
	protected Dict doExecute(AiNodeDefinition node, FlowContextHolder context) {
		try {
			// 1. 验证HTTP节点配置
			AiHttpNode config = validateHttpConfig(node);

			// 2. 准备变量环境
			Map<String, Object> variables = prepareVariables(node, context);

			// 3. 构建HTTP请求
			String url = config.getUrl();
			url = processUrlParameters(config, url, variables);
			Method httpMethod = Method.valueOf(config.getMethod());

			// 4. 创建请求对象
			HttpRequest request = HttpRequest.of(url).method(httpMethod);

			// 5. 设置请求头
			addRequestHeaders(request, config, variables);

			// 6. 处理请求体
			addRequestBody(request, httpMethod, config, variables, context);

			// 7. 执行HTTP请求
			HttpResponse response = request.execute();

			// 8. 封装并返回响应结果
			return buildResponseResult(response);

		}
		catch (Exception e) {
			// 异常处理：包装异常并提供更详细的错误信息
			log.error("[HTTP节点] 执行HTTP请求失败: {}", e.getMessage(), e);
			throw FlowException.nodeError(node.getId(), "[HTTP节点] -> " + e.getMessage());
		}
	}

	/**
	 * 验证HTTP节点配置的有效性
	 * @param node HTTP节点定义
	 * @return 验证通过的HTTP节点配置
	 * @throws FlowException 如果配置无效则抛出异常
	 */
	private AiHttpNode validateHttpConfig(AiNodeDefinition node) {
		AiHttpNode config = node.getHttpParams();
		if (config == null) {
			throw FlowException.invalidParam("HTTP节点配置无效：配置对象为空");
		}
		if (config.getUrl() == null || config.getMethod() == null) {
			throw FlowException.invalidParam("HTTP节点配置无效：URL或HTTP方法未设置");
		}
		return config;
	}

	/**
	 * 准备变量环境，合并输入变量、上下文变量和参数
	 * @param node 节点定义
	 * @param context 流程上下文
	 * @return 合并后的变量映射
	 */
	private Map<String, Object> prepareVariables(AiNodeDefinition node, FlowContextHolder context) {
		Dict inputVariables = getInputVariables(node, context);
		return MapUtil.<String, Object>builder()
			.putAll(inputVariables)
			.putAll(context.getVariables())
			.putAll(context.getParameters())
			.build();
	}

	/**
	 * 设置HTTP请求头
	 * @param request HTTP请求对象
	 * @param config HTTP节点配置
	 * @param variables 变量映射
	 */
	private void addRequestHeaders(HttpRequest request, AiHttpNode config, Map<String, Object> variables) {
		List<AiParamDefinition> headerParams = config.getHeaderParams();
		if (headerParams != null) {
			for (AiParamDefinition paramNode : headerParams) {
				request.header(paramNode.getName(), MapUtil.getStr(variables, paramNode.getType()));
			}
		}
	}

	/**
	 * 处理URL参数，将参数添加到URL中
	 * @param config HTTP节点配置
	 * @param url 原始URL
	 * @param variables 变量映射
	 * @return 处理后的URL（包含查询参数）
	 */
	private String processUrlParameters(AiHttpNode config, String url, Map<String, Object> variables) {
		List<AiParamDefinition> paramParams = config.getParamsParams();
		if (paramParams != null) {
			for (AiParamDefinition paramNode : paramParams) {
				url = addQueryParam(url, paramNode.getName(), MapUtil.getStr(variables, paramNode.getType()));
			}
		}
		return url;
	}

	/**
	 * 根据HTTP方法添加请求体
	 * @param request HTTP请求对象
	 * @param httpMethod HTTP请求方法
	 * @param config HTTP节点配置
	 * @param variables 变量映射
	 * @param context 流程上下文
	 */
	private void addRequestBody(HttpRequest request, Method httpMethod, AiHttpNode config,
			Map<String, Object> variables, FlowContextHolder context) {
		// GET和DELETE请求默认不需要请求体
		if (Method.GET.equals(httpMethod) || Method.DELETE.equals(httpMethod)) {
			return;
		}

		// POST和PUT请求需要处理请求体
		if (Method.POST.equals(httpMethod) || Method.PUT.equals(httpMethod)) {
			// 处理表单格式的请求体
			List<AiParamDefinition> bodyParams = config.getBodyParams();
			if (StrUtil.equalsAnyIgnoreCase(ContentType.FORM_URLENCODED.getValue(), config.getContentType())
					&& Objects.nonNull(bodyParams)) {
				for (AiParamDefinition paramNode : bodyParams) {
					if (Objects.nonNull(context.getVariable(paramNode.getType()))) {
						request.form(paramNode.getName(), MapUtil.getStr(variables, paramNode.getType()));
					}
				}
			}

			// 处理JSON格式的请求体
			String jsonBody = config.getJsonBody();
			if (StrUtil.equalsAnyIgnoreCase(ContentType.JSON.getValue(), config.getContentType())
					&& StrUtil.isNotBlank(jsonBody)) {
				// 使用模板引擎渲染JSON请求体
				Template jsonBodyTemplate = engine.getTemplate(jsonBody);
				String renderedBody = jsonBodyTemplate.render(variables);
				request.body(renderedBody);
			}
		}

	}

	/**
	 * 构建响应结果
	 * @param response HTTP响应
	 * @return 封装后的响应结果字典
	 */
	private Dict buildResponseResult(HttpResponse response) {
		// 收集响应头信息
		Map<String, Object> variables = MapUtil.newHashMap();
		response.headers().forEach((k, v) -> {
			if (k != null) {
				variables.put(k, v);
			}
		});
		return Dict.create()
			.set(FlowConstant.HTTP_STATUS, response.getStatus())
			.set(FlowConstant.HTTP_HEADERS, variables)
			.set(FlowConstant.HTTP_BODY, response.body())
			.set(FlowConstant.TIMESTAMP, System.currentTimeMillis());
	}

}
