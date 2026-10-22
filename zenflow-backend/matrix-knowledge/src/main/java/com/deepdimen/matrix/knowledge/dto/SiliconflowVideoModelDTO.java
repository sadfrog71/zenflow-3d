package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

import java.util.List;

/**
 * 硅流视频模型数据传输对象 用于处理视频模型相关的请求和响应数据
 *
 * @author caoyuan
 * @date 2025/05/07
 */
@Data
public class SiliconflowVideoModelDTO {

	/**
	 * 视频请求模型响应类 用于封装视频生成请求的初始响应数据
	 *
	 * @author caoyuan
	 * @date 2025/05/07
	 */
	@Data
	public static class VideoRequestModelResponse {

		/**
		 * 请求ID 用于查询视频生成状态的唯一标识符
		 */
		private String requestId;

	}

	/**
	 * 视频查询模型响应类 用于封装查询视频生成状态的响应数据
	 */
	@Data
	public static class VideoGetModelResponse {

		/**
		 * 生成状态 表示视频生成的当前状态，如processing、completed、failed等
		 */
		private String status;

		/**
		 * 失败原因 当状态为failed时，说明失败的具体原因
		 */
		private String reason;

		/**
		 * 视频生成结果 包含生成的视频信息
		 */
		private VideoGetModelResult results;

	}

	/**
	 * 视频生成结果内部类 封装生成的视频列表
	 */
	@Data
	public static class VideoGetModelResult {

		/**
		 * 视频列表 包含所有生成的视频信息
		 */
		private List<Video> videos;

	}

	/**
	 * 视频内部类 封装单个视频的信息
	 */
	@Data
	public static class Video {

		/**
		 * 视频URL 生成的视频的访问地址
		 */
		private String url;

	}

}
