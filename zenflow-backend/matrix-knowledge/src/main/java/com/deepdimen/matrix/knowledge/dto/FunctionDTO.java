package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

/**
 * 函数数据传输对象
 * <p>
 * 封装函数脚本信息，用于动态执行函数功能
 *
 * @author caoyuan
 * @date 2024/08/07
 */
@Data
@FieldNameConstants
public class FunctionDTO {

	/**
	 * 函数脚本
	 * <p>
	 * 包含可执行的函数脚本代码内容
	 */
	String script;

}
