package com.deepdimen.matrix.knowledge.dto;

import com.deepdimen.matrix.knowledge.support.annotation.FieldPrompt;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

/**
 * AI基础请求类
 * <p>
 * 所有AI相关请求的基类，包含通用的请求参数
 *
 * @author caoyuan
 * @date 2024/4/24
 */
@Data
@FieldNameConstants
public class BaseAiRequest {

	/**
	 * 消息键值
	 * <p>
	 * 用于标识消息的唯一键，格式为Long类型的字符串表示
	 */
	@FieldPrompt("用户输入的 messageKey 字段内容，其格式为表示 Long 类型的字符串")
	private String messageKey;

}
