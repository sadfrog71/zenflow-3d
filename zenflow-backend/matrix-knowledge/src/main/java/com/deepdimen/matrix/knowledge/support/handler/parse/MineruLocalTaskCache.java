package com.deepdimen.matrix.knowledge.support.handler.parse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * MinerU本地版异步解析结果缓存
 *
 * @author matrix
 * @date 2026/05/18
 */
@Slf4j
@Component
public class MineruLocalTaskCache {

	private static final long EXPIRE_MILLIS = TimeUnit.MINUTES.toMillis(30);

	private final ConcurrentHashMap<String, MineruLocalTaskResult> cache = new ConcurrentHashMap<>();

	@Data
	@AllArgsConstructor
	public static class MineruLocalTaskResult {

		private boolean success;

		private String content;

		private long createTime;

		/**
		 * 图片资源映射：图片文件名 -> base64数据(dataURL格式)
		 */
		private Map<String, String> images;

	}

	public void putSuccess(String taskId, String mdContent) {
		putSuccess(taskId, mdContent, null);
	}

	public void putSuccess(String taskId, String mdContent, Map<String, String> images) {
		cache.put(taskId, new MineruLocalTaskResult(true, mdContent, System.currentTimeMillis(), images));
	}

	public void putFailed(String taskId, String error) {
		cache.put(taskId, new MineruLocalTaskResult(false, error, System.currentTimeMillis(), null));
	}

	public MineruLocalTaskResult get(String taskId) {
		return cache.get(taskId);
	}

	public void remove(String taskId) {
		cache.remove(taskId);
	}

	@Scheduled(fixedRate = 10, timeUnit = TimeUnit.MINUTES)
	public void cleanExpired() {
		long now = System.currentTimeMillis();
		cache.entrySet().removeIf(entry -> (now - entry.getValue().getCreateTime()) > EXPIRE_MILLIS);
	}

}
