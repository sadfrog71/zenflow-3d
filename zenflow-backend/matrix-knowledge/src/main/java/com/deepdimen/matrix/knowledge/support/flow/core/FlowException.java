package com.deepdimen.matrix.knowledge.support.flow.core;

import com.deepdimen.matrix.common.core.exception.CheckedException;
import lombok.Getter;

/**
 * AI工作流异常类 定义了流程执行过程中可能出现的各种异常情况 继承自CheckedException，用于处理可预期的业务异常
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Getter
public class FlowException extends CheckedException {

	/**
	 * 错误代码 - 用于标识异常类型
	 */
	private final String code;

	/**
	 * 错误消息 - 异常的详细描述
	 */
	private final String message;

	/**
	 * 附加数据 - 可选的异常相关数据
	 */
	private final Object data;

	/**
	 * 构造函数 - 仅指定错误消息
	 * @param message 错误消息
	 */
	public FlowException(String message) {
		this("FLOW_ERROR", message, null);
	}

	/**
	 * 构造函数 - 指定错误代码和消息
	 * @param code 错误代码
	 * @param message 错误消息
	 */
	public FlowException(String code, String message) {
		this(code, message, null);
	}

	/**
	 * 构造函数 - 完整参数构造
	 * @param code 错误代码
	 * @param message 错误消息
	 * @param data 附加数据
	 */
	public FlowException(String code, String message, Object data) {
		super(message);
		this.code = code;
		this.message = message;
		this.data = data;
	}

	/**
	 * 创建节点执行异常 - 通用的执行错误
	 * @param message 错误消息
	 * @return FlowException实例
	 */
	public static FlowException executeError(String message) {
		return new FlowException("EXECUTE_ERROR", message);
	}

	/**
	 * 创建节点执行异常 - 特定节点的执行错误
	 * @param nodeId 节点ID
	 * @param message 错误消息
	 * @return FlowException实例
	 */
	public static FlowException nodeError(String nodeId, String message) {
		return new FlowException("NODE_ERROR", String.format("节点[%s]执行失败 -> %s", nodeId, message));
	}

	/**
	 * 创建参数验证异常 - 输入参数不合法
	 * @param message 错误消息
	 * @return FlowException实例
	 */
	public static FlowException invalidParam(String message) {
		return new FlowException("INVALID_PARAM", message);
	}

	/**
	 * 创建工作流验证异常 - 流程配置或结构错误
	 * @param message 错误消息
	 * @return FlowException实例
	 */
	public static FlowException invalidFlow(String message) {
		return new FlowException("INVALID_FLOW", message);
	}

	/**
	 * 创建API密钥异常 - API密钥无效或缺失
	 * @param message 错误消息
	 * @return FlowException实例
	 */
	public static FlowException invalidApiKey(String message) {
		return new FlowException("INVALID_API_KEY", message);
	}

	/**
	 * 创建DSL异常 - 流程定义语言格式错误
	 * @param message 错误消息
	 * @return FlowException实例
	 */
	public static FlowException invalidDSL(String message) {
		return new FlowException("INVALID_DSL", message);
	}

}
