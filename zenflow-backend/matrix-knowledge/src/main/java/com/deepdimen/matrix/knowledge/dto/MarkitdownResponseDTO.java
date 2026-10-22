package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

/**
 * Markitdown响应数据传输对象 用于封装Markitdown服务返回的响应数据
 *
 * @author caoyuan
 * @date 2024/10/23
 */
@Data
public class MarkitdownResponseDTO {

	/**
	 * 响应文本内容 包含Markitdown处理后的文本数据
	 */
	private String text;

}
