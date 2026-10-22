package com.deepdimen.matrix.knowledge.support.flow.model.nodes;

import lombok.Data;

/**
 * AI 代码节点 用于在工作流中执行自定义代码逻辑
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Data
public class AiCodeNode {

	/**
	 * 代码内容 存储需要执行的代码脚本，支持多种脚本语言
	 */
	private String code;

}
