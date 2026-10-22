package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

/**
 * AI编辑器代码补全数据传输对象
 * <p>
 * 用于编辑器中AI辅助代码补全功能的请求和响应数据传输
 *
 * @author caoyuan
 * @date 2025/05/04
 */
@Data
public class AiEditorCompletionsDTO {

	/**
	 * 角色标识 用于标识补全请求的角色，如：user（用户）、system（系统）、assistant（助手）
	 */
	private String role;

	/**
	 * 补全内容 需要进行补全的代码片段或上下文内容
	 */
	private String content;

}
