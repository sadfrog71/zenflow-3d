package com.deepdimen.matrix.knowledge.support.flow.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI流程执行结果 封装了流程执行完成后的所有结果信息 包括执行状态、输出数据、性能指标等
 *
 * @author caoyuan
 * @date 2025/3/3
 */
@Data
public class AiFlowExecuteResult {

	/**
	 * 执行的节点列表 - 按执行顺序记录的所有已执行节点 包含每个节点的执行状态、输出等详细信息
	 */
	@Schema(description = "执行的节点列表")
	private List<AiNodeDefinition> nodes;

	/**
	 * 执行参数 - 流程执行过程中产生的所有变量和中间结果 键值对形式存储，包含各节点的输出变量
	 */
	@Schema(description = "执行参数")
	private Map<String, Object> variables;

	/**
	 * 执行结果 - 流程的最终输出结果 通常是最后一个节点的输出或指定的结果变量
	 */
	@Schema(description = "执行结果")
	private Object result;

	/**
	 * 执行状态 - 流程的整体执行状态 如：success(成功)、error(错误)、pending(待定)等
	 */
	@Schema(description = "执行状态")
	private String executed;

	/**
	 * 错误信息 - 执行失败时的错误描述 仅在执行出错时有值
	 */
	@Schema(description = "错误信息")
	private String error;

	/**
	 * 执行时长 - 流程总执行时间（毫秒） 从开始到结束的总耗时
	 */
	@Schema(description = "执行时长(ms)")
	private Long duration;

	/**
	 * Token总使用量 - 所有LLM节点消耗的Token总数 用于计费和资源使用统计
	 */
	@Schema(description = "Token总使用量")
	private Long totalTokens;

	@Override
	public String toString() {
		return "FlowExecuteResult{" + "nodes=" + nodes + ", variables=" + variables + ", result=" + result
				+ ", executed=" + executed + ", error=" + error + ", duration=" + duration + ", totalTokens="
				+ totalTokens + '}';
	}

}
