package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

import java.util.List;

/**
 * OCR结果模型数据传输对象 用于封装OCR识别操作的结果数据
 *
 * @author caoyuan
 * @date 2024/3/13
 */
@Data
public class OcrResultModelDTO {

	/**
	 * OCR操作的状态码 成功的操作通常由状态码200表示
	 */
	private int statusCode;

	/**
	 * OCR操作的结果列表 每个结果包括识别的文本、得分和文本在图像中的位置
	 */
	private List<OcrResult> results;

	/**
	 * OCR结果内部类 表示单个OCR操作的结果
	 */
	@Data
	public static class OcrResult {

		/**
		 * OCR操作识别出的文本内容
		 */
		private String text;

		/**
		 * OCR操作的得分 表示识别文本的置信度水平
		 */
		private double score;

		/**
		 * 识别文本在图像中的位置 以列表的列表形式表示，其中每个内部列表包含一个点的坐标
		 */
		private List<List<Double>> position;

	}

}
