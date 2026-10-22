package com.deepdimen.matrix.knowledge.support.flow.model.nodes;

import com.deepdimen.matrix.knowledge.support.flow.model.AiParamDefinition;
import lombok.Data;

import java.util.List;

/**
 * AI HTTP 节点 用于在工作流中发送HTTP请求，支持与外部API接口进行交互
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Data
public class AiHttpNode {

	/**
	 * 请求URL地址 完整的HTTP/HTTPS请求地址
	 */
	private String url;

	/**
	 * HTTP请求方法 支持GET、POST、PUT、DELETE、PATCH等标准HTTP方法
	 */
	private String method;

	/**
	 * 请求内容类型 例如：application/json、application/x-www-form-urlencoded、multipart/form-data等
	 */
	private String contentType;

	/**
	 * 请求头参数列表 用于设置自定义的HTTP请求头，如Authorization、User-Agent等
	 */
	private List<AiParamDefinition> headerParams;

	/**
	 * URL查询参数列表 用于设置GET请求的查询参数或其他请求方法的URL参数
	 */
	private List<AiParamDefinition> paramsParams;

	/**
	 * 请求体表单参数列表 用于设置表单格式的请求体参数
	 */
	private List<AiParamDefinition> bodyParams;

	/**
	 * JSON格式的请求体 当contentType为application/json时使用，直接设置JSON字符串作为请求体
	 */
	private String jsonBody;

}
