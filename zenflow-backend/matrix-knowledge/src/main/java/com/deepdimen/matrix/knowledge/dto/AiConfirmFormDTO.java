package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

import java.util.HashMap;

/**
 * AI确认表单DTO
 *
 * @author caoyuan
 * @date 2025/07/31
 */
@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
public class AiConfirmFormDTO extends HashMap<String, Object> {

	/**
	 * 功能名称
	 * <p>
	 * 调用的功能或工具名称
	 */
	private String funcName;

}
