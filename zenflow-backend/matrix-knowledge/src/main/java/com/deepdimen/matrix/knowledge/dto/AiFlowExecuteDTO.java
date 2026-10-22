package com.deepdimen.matrix.knowledge.dto;

import cn.hutool.core.lang.Dict;
import com.deepdimen.matrix.knowledge.support.flow.model.AiNodeDefinition;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * AI流程执行数据传输对象
 * <p>
 * 用于AI工作流的执行控制，支持流式处理和回调机制
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Data
public class AiFlowExecuteDTO {

	/**
	 * 流程ID 需要执行的AI工作流唯一标识
	 */
	private Long id;

	/**
	 * 会话ID 用于关联同一次对话的多轮交互
	 */
	private String conversationId;

	/**
	 * 执行参数 流程执行所需的输入参数集合
	 */
	private Dict params;

	/**
	 * 环境配置 流程执行的环境变量配置
	 */
	private Dict envs;

	/**
	 * 是否流式处理 true：流式返回结果，false：等待完整结果
	 */
	private boolean stream;

	/**
	 * 流程执行回调 用于处理流程执行过程中的事件通知
	 */
	@Setter
	@Getter
	private FlowCallback callback;

	/**
	 * 流程回调函数式接口
	 * <p>
	 * 定义流程执行过程中的回调处理方法
	 *
	 * @author caoyuan
	 * @date 2025/06/25
	 */
	@FunctionalInterface
	public interface FlowCallback {

		/**
		 * 执行回调
		 * @param result 回调结果
		 */
		void execute(FlowCallbackResult result);

	}

	/**
	 * 流程回调数据实体类
	 * <p>
	 * 封装流程执行过程中的状态和数据信息
	 *
	 * @author caoyuan
	 * @date 2025/06/25
	 */
	@Builder
	@Getter
	@Setter
	public static class FlowCallbackData {

		/**
		 * 执行状态 如：start（开始）、running（运行中）、success（成功）、failed（失败）
		 */
		private String status;

		/**
		 * 失败原因 当status为failed时，记录失败的具体原因
		 */
		private String reason;

		/**
		 * 节点类型 当前执行的节点类型，如：llm、knowledge、code等
		 */
		private String nodeType;

		/**
		 * 节点ID 当前执行节点的唯一标识
		 */
		private String nodeId;

		/**
		 * 执行耗时 节点执行的时间（毫秒）
		 */
		private Long duration;

		/**
		 * 节点列表 流程中所有节点的定义信息
		 */
		private List<AiNodeDefinition> nodes;

		/**
		 * 下一个节点ID列表 即将执行的下一个节点标识
		 */
		private List<String> nextNodeIds;

		/**
		 * Token使用情况 LLM节点的token消耗统计
		 */
		private Object tokens;

		/**
		 * 是否结束 标识流程是否已经执行完毕
		 */
		private Boolean isEnd;

		/**
		 * 输出内容 节点执行的输出结果
		 */
		private String content;

		/**
		 * 是否思考中 标识AI是否正在思考处理
		 */
		private Boolean isThinking;

	}

	/**
	 * 流程回调结果类
	 * <p>
	 * 封装回调消息的类型和数据
	 *
	 * @author caoyuan
	 * @date 2025/06/25
	 */
	@Builder
	@Getter
	@Setter
	public static class FlowCallbackResult {

		/**
		 * 消息类型 如：node_start、node_end、flow_end、error等
		 */
		private String msgType;

		/**
		 * 回调数据 具体的回调数据内容
		 */
		private FlowCallbackData data;

	}

}
