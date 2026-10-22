package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

import java.util.Map;

/**
 * Mineru本地响应DTO
 *
 * @author caoyuan
 * @date 2025/10/19
 */
@Data
public class MineruLocalResponseDTO {

	private String status;

	private ResultDTO result;

	private String error;

	@Data
	public static class ResultDTO {

		private String fileName;

		private String md;

		/**
		 * 图片资源映射：图片文件名 -> base64数据(dataURL格式)
		 */
		private Map<String, String> images;

	}

}
