package com.deepdimen.matrix.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * AI视频生成完成数据传输对象
 * <p>
 * 用于传输AI视频生成所需的参数和配置信息，支持文生视频、图生视频等功能
 *
 * @author caoyuan
 * @date 2025/05/07
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY) // 仅当字段非 null 且非 "" 时才序列化
public class AiVideoCompletionsDTO {

	/**
	 * 正向提示词
	 * <p>
	 * 描述想要生成的视频内容的详细文本提示
	 */
	@JsonProperty("prompt")
	@JsonAlias("prompt")
	private String prompt;

	/**
	 * 负向提示词
	 * <p>
	 * 描述不希望在视频中出现的内容，用于优化生成效果
	 */
	@JsonProperty("negative_prompt")
	@JsonAlias("negativePrompt")
	private String negativePrompt;

	/**
	 * 视频画面尺寸
	 * <p>
	 * 指定生成视频的分辨率，如："1280x720"、"1920x1080"等
	 */
	@JsonProperty("image_size")
	@JsonAlias("imageSize")
	private String imageSize;

	/**
	 * 随机种子值
	 * <p>
	 * 用于控制生成结果的随机性，相同种子值可复现相同结果
	 */
	@JsonProperty("seed")
	@JsonAlias("seed")
	private Integer seed;

	/**
	 * 参考图片
	 * <p>
	 * 图生视频模式下的输入图片URL或Base64编码
	 */
	@JsonProperty("image")
	@JsonAlias("image")
	private String image;

	/**
	 * AI模型标识
	 * <p>
	 * 指定用于生成视频的AI模型，如："stable-video-diffusion"等
	 */
	@JsonProperty("model")
	@JsonAlias("model")
	private String model;

}
