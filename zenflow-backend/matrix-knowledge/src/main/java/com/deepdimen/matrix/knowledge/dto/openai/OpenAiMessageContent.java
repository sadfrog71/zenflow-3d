package com.deepdimen.matrix.knowledge.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * OpenAI 消息内容项 支持不同类型的内容：text, image_url 等
 *
 * @author caoyuan
 * @date 2025/01/14
 */
@Data
public class OpenAiMessageContent implements Serializable {

	/**
	 * 内容类型: text, image_url
	 */
	private String type;

	/**
	 * 文本内容（当type=text时使用）
	 */
	private String text;

	/**
	 * 图片URL信息（当type=image_url时使用）
	 */
	@JsonProperty("image_url")
	private OpenAiImageUrl imageUrl;

	public OpenAiMessageContent() {
	}

	public OpenAiMessageContent(String type, String text) {
		this.type = type;
		this.text = text;
	}

	public OpenAiMessageContent(String type, OpenAiImageUrl imageUrl) {
		this.type = type;
		this.imageUrl = imageUrl;
	}

}