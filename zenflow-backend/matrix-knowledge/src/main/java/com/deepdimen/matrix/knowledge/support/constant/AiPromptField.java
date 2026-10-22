package com.deepdimen.matrix.knowledge.support.constant;

/**
 * AI提示词字段常量接口
 * <p>
 * 定义了AI提示词模板中可用的占位符字段，用于动态替换提示词中的变量
 *
 * @author caoyuan
 * @date 2025/3/12
 */
public interface AiPromptField {

	/**
	 * 系统时间占位符
	 * <p>
	 * 在提示词模板中使用此字段，会被替换为当前系统时间 例如：今天是${systemTime}，请帮我分析...
	 */
	String systemTime = "systemTime";

}
