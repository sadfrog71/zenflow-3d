package com.deepdimen.matrix.knowledge.service;

import com.deepdimen.matrix.knowledge.dto.dbgpt.DbgptChatRequest;
import com.deepdimen.matrix.knowledge.dto.dbgpt.DbgptChatResponse;

/**
 * DBGPT API服务接口
 * <p>
 * 提供与DBGPT API交互的功能，支持基于自然语言的数据库查询
 *
 * @author caoyuan
 * @date 2024/10/9
 */
public interface DbgptApiService {

	/**
	 * 发送聊天请求到DBGPT API
	 * @param request DBGPT聊天请求
	 * @return DBGPT聊天响应
	 */
	DbgptChatResponse chatCompletions(DbgptChatRequest request);

}
