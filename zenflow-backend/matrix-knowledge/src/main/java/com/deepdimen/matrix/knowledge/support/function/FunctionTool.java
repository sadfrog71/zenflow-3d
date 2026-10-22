package com.deepdimen.matrix.knowledge.support.function;

import com.deepdimen.matrix.knowledge.dto.BaseAiRequest;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;

/**
 * 函数工具接口
 *
 * 该接口定义了一个简化版的AI函数工具 继承自ToolExecutor，提供基本的函数工具功能
 *
 * @author caoyuan
 * @date 2024/9/27
 */
public interface FunctionTool<T extends BaseAiRequest> extends ToolExecutor {

	/**
	 * 前端是否展示此函数 默认返回true，表示该函数工具会在前端显示
	 * @return boolean true-展示，false-隐藏
	 */
	default boolean showFunction() {
		return true;
	}

	/**
	 * 函数名称 子类必须实现此方法，返回函数工具的唯一名称
	 * @return String 函数工具名称
	 */
	String name();

	/**
	 * 执行函数工具 当AI决定调用此函数工具时，会通过此方法执行 默认返回空字符串，子类可以重写此方法实现具体逻辑
	 * @param toolExecutionRequest 工具执行请求，包含函数参数
	 * @param memoryId 内存ID（用于多轮对话的上下文管理）
	 * @return {@link String } 执行结果
	 */
	@Override
	default String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
		return "";
	}

}
