package com.deepdimen.matrix.knowledge.dto.dbgpt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DBGPT聊天响应DTO
 * <p>
 * 用于接收DBGPT API响应的数据传输对象
 *
 * @author caoyuan
 * @date 2024/10/9
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DbgptChatResponse {

	/**
	 * 响应ID
	 */
	private String id;

	/**
	 * 对象类型
	 */
	private String object;

	/**
	 * 创建时间戳
	 */
	private Long created;

	/**
	 * 使用的模型
	 */
	private String model;

	/**
	 * 选择列表
	 */
	private List<Choice> choices;

	/**
	 * 使用统计信息
	 */
	private Usage usage;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Choice {

		/**
		 * 选择索引
		 */
		private Integer index;

		/**
		 * 消息内容
		 */
		private Message message;

		/**
		 * 完成原因
		 */
		private String finish_reason;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Message {

		/**
		 * 角色
		 */
		private String role;

		/**
		 * 内容
		 */
		private String content;

	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Usage {

		/**
		 * 提示词token数
		 */
		private Integer prompt_tokens;

		/**
		 * 总token数
		 */
		private Integer total_tokens;

		/**
		 * 完成token数
		 */
		private Integer completion_tokens;

	}

}
