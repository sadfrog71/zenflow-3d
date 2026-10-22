package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.deepdimen.matrix.knowledge.config.DbgptProperties;
import com.deepdimen.matrix.knowledge.dto.dbgpt.DbgptChatRequest;
import com.deepdimen.matrix.knowledge.dto.dbgpt.DbgptChatResponse;
import com.deepdimen.matrix.knowledge.service.DbgptApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * DBGPT API服务实现类
 * <p>
 * 实现与DBGPT API的HTTP通信，支持基于自然语言的数据库查询
 *
 * @author caoyuan
 * @date 2024/10/9
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbgptApiServiceImpl implements DbgptApiService {

	private final DbgptProperties dbgptProperties;

	/**
	 * 发送聊天请求到DBGPT API
	 * @param request DBGPT聊天请求
	 * @return DBGPT聊天响应
	 */
	@Override
	public DbgptChatResponse chatCompletions(DbgptChatRequest request) {
		try {
			String url = dbgptProperties.getBaseUrl() + "/api/v2/chat/completions";

			log.info("Sending DBGPT request to: {}, request: {}", url, JSONUtil.toJsonStr(request));

			HttpResponse response = HttpRequest.post(url)
				.header("Authorization", "Bearer " + dbgptProperties.getKey())
				.header("Accept", "application/json")
				.header("Content-Type", "application/json")
				.body(JSONUtil.toJsonStr(request))
				.timeout(dbgptProperties.getTimeout())
				.execute();

			if (!response.isOk()) {
				log.error("DBGPT API request failed with status: {}, body: {}", response.getStatus(), response.body());
				throw new RuntimeException("DBGPT API请求失败: " + response.getStatus() + " - " + response.body());
			}

			String responseBody = response.body();
			log.info("DBGPT API response: {}", responseBody);

			if (StrUtil.isBlank(responseBody)) {
				throw new RuntimeException("DBGPT API返回空响应");
			}

			return JSONUtil.toBean(responseBody, DbgptChatResponse.class);

		}
		catch (Exception e) {
			log.error("调用DBGPT API异常", e);
			throw new RuntimeException("调用DBGPT API异常: " + e.getMessage(), e);
		}
	}

}
