package com.deepdimen.matrix.knowledge.support.flow.model.nodes;

import lombok.Data;

import java.util.List;

/**
 * AI 条件分支节点 用于在工作流中实现条件判断和分支控制，支持基于变量值或代码逻辑的多路分支
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Data
public class AiSwitchNode {

	/**
	 * 条件判断代码 可选的自定义代码逻辑，用于复杂的条件判断
	 */
	private String code;

	/**
	 * 判断变量名 需要进行条件判断的变量名称，从流程上下文中获取变量值
	 */
	private String variable;

	/**
	 * 分支条件列表 定义所有可能的分支路径及其对应的条件
	 */
	private List<Case> cases;

	/**
	 * 分支条件类 定义单个分支的条件和名称
	 */
	@Data
	public static class Case {

		/**
		 * 分支名称 该分支的可读名称，用于流程图显示
		 */
		private String name;

		/**
		 * 分支条件值 当变量值等于此值时，流程进入该分支
		 */
		private String value;

	}

}
