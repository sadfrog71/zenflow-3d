package com.deepdimen.matrix.knowledge.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * OpenAI 消息DTO 支持多模态内容（文本、图片等）
 *
 * @author caoyuan
 * @date 2025/01/14
 */
@Data
public class OpenAiMessage implements Serializable {

	/**
	 * 消息角色: system, user, assistant
	 */
	@NotBlank(message = "消息角色不能为空")
	private String role;

	/**
	 * 消息内容，可以是： 1. String: 简单文本内容 2. List<OpenAiMessageContent>: 多模态内容（文本+图片等）
	 */
	@NotNull(message = "消息内容不能为空")
	private Object content;

	/**
	 * 消息名称（可选）
	 */
	private String name;

	public OpenAiMessage() {
	}

	public OpenAiMessage(String role, String content) {
		this.role = role;
		this.content = content;
	}

	public OpenAiMessage(String role, Object content) {
		this.role = role;
		this.content = content;
	}

	/**
	 * 获取内容的字符串表示（用于兼容处理）
	 */
	public String getContentAsString() {
		if (content instanceof String) {
			return (String) content;
		}
		else if (content instanceof List) {
			@SuppressWarnings("unchecked")
			List<?> contentList = (List<?>) content;
			return contentList.stream().filter(item -> {
				if (item instanceof OpenAiMessageContent) {
					return "text".equals(((OpenAiMessageContent) item).getType());
				}
				else if (item instanceof java.util.Map) {
					@SuppressWarnings("unchecked")
					java.util.Map<String, Object> map = (java.util.Map<String, Object>) item;
					return "text".equals(map.get("type"));
				}
				return false;
			}).map(item -> {
				if (item instanceof OpenAiMessageContent) {
					return ((OpenAiMessageContent) item).getText();
				}
				else if (item instanceof java.util.Map) {
					@SuppressWarnings("unchecked")
					java.util.Map<String, Object> map = (java.util.Map<String, Object>) item;
					return (String) map.get("text");
				}
				return "";
			}).reduce("", (a, b) -> a + " " + b).trim();
		}
		return "";
	}

	/**
	 * 检查是否包含图片内容
	 */
	public boolean hasImageContent() {
		if (content instanceof List) {
			@SuppressWarnings("unchecked")
			List<?> contentList = (List<?>) content;
			return contentList.stream().anyMatch(item -> {
				if (item instanceof OpenAiMessageContent) {
					return "image_url".equals(((OpenAiMessageContent) item).getType());
				}
				else if (item instanceof java.util.Map) {
					@SuppressWarnings("unchecked")
					java.util.Map<String, Object> map = (java.util.Map<String, Object>) item;
					return "image_url".equals(map.get("type"));
				}
				return false;
			});
		}
		return false;
	}

	/**
	 * 获取所有图片URL
	 */
	public List<String> getImageUrls() {
		if (content instanceof List) {
			@SuppressWarnings("unchecked")
			List<?> contentList = (List<?>) content;
			return contentList.stream().filter(item -> {
				if (item instanceof OpenAiMessageContent) {
					return "image_url".equals(((OpenAiMessageContent) item).getType());
				}
				else if (item instanceof java.util.Map) {
					@SuppressWarnings("unchecked")
					java.util.Map<String, Object> map = (java.util.Map<String, Object>) item;
					return "image_url".equals(map.get("type"));
				}
				return false;
			}).map(item -> {
				if (item instanceof OpenAiMessageContent) {
					return ((OpenAiMessageContent) item).getImageUrl().getUrl();
				}
				else if (item instanceof java.util.Map) {
					@SuppressWarnings("unchecked")
					java.util.Map<String, Object> map = (java.util.Map<String, Object>) item;
					@SuppressWarnings("unchecked")
					java.util.Map<String, Object> imageUrlMap = (java.util.Map<String, Object>) map.get("image_url");
					return imageUrlMap != null ? (String) imageUrlMap.get("url") : null;
				}
				return null;
			}).filter(url -> url != null).toList();
		}
		return List.of();
	}

}