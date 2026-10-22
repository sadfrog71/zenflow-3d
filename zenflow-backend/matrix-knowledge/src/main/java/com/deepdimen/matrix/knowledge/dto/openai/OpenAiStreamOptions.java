package com.deepdimen.matrix.knowledge.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * OpenAI 流式选项
 *
 * @author caoyuan
 * @date 2025/01/14
 */
@Data
public class OpenAiStreamOptions implements Serializable {

	/**
	 * 是否在流式响应中包含使用统计信息
	 */
	@JsonProperty("include_usage")
	private Boolean includeUsage = false;

	public OpenAiStreamOptions() {
	}

	public OpenAiStreamOptions(Boolean includeUsage) {
		this.includeUsage = includeUsage;
	}

}