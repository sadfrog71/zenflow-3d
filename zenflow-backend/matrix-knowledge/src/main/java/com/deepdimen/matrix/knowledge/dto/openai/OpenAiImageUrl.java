package com.deepdimen.matrix.knowledge.dto.openai;

import lombok.Data;

import java.io.Serializable;

/**
 * OpenAI 图片URL信息
 *
 * @author caoyuan
 * @date 2025/01/14
 */
@Data
public class OpenAiImageUrl implements Serializable {

	/**
	 * 图片URL，支持HTTP URL和data URI格式
	 */
	private String url;

	/**
	 * 图片详细程度: low, high, auto（可选）
	 */
	private String detail;

	public OpenAiImageUrl() {
	}

	public OpenAiImageUrl(String url) {
		this.url = url;
	}

	public OpenAiImageUrl(String url, String detail) {
		this.url = url;
		this.detail = detail;
	}

}