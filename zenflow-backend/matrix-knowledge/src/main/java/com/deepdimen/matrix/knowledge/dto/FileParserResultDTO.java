package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

/**
 * 文件解析结果数据传输对象
 * <p>
 * 封装文件解析操作的执行结果和相关信息
 *
 * @author caoyuan
 * @date 2024/10/2
 */
@Data
public class FileParserResultDTO {

	/**
	 * 解析结果
	 * <p>
	 * 标识文件解析是否成功，true表示成功，false表示失败
	 */
	private boolean result;

	/**
	 * 结果消息
	 * <p>
	 * 解析结果的详细描述信息，包括成功信息或错误信息
	 */
	private String message;

}
