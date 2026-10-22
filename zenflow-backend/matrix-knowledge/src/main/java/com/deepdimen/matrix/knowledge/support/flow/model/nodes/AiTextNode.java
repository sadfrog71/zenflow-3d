package com.deepdimen.matrix.knowledge.support.flow.model.nodes;

import lombok.Data;

/**
 * AI 文本节点 用于在工作流中返回固定文本内容，支持模板变量替换 适用于需要输出静态文本、提示信息、模板消息等场景
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Data
public class AiTextNode {

	/**
	 * 文本内容 返回的固定文本内容，支持使用${变量名}进行模板变量替换 例如："欢迎用户${username}，您的订单${orderId}已创建成功！"
	 */
	private String content;

}