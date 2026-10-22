package com.deepdimen.matrix.knowledge.dto.dbgpt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DBGPT聊天请求DTO
 * <p>
 * 用于向DBGPT API发送聊天请求的数据传输对象
 *
 * @author caoyuan
 * @date 2024/10/9
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbgptChatRequest {

	/**
	 * 用户消息内容
	 */
	private String messages;

	/**
	 * 使用的模型名称，如：gpt-4o
	 */
	private String model;

	/**
	 * 聊天模式，如：chat_data
	 */
	private String chat_mode;

	/**
	 * 聊天参数，通常是数据库名称
	 */
	private String chat_param;

}
