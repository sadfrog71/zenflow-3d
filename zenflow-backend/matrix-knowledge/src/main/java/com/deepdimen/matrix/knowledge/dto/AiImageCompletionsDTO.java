package com.deepdimen.matrix.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

import java.util.List;

/**
 * AI图像生成数据传输对象
 * <p>
 * 用于AI图像生成和编辑任务的参数传递，支持文生图和图生图功能
 *
 * @author caoyuan
 * @date 2025/05/07
 **/
@Data
@FieldNameConstants
@JsonInclude(JsonInclude.Include.NON_EMPTY) // 仅当字段非 null 且非 "" 时才序列化
public class AiImageCompletionsDTO {

	/**
	 * 正向提示词 描述想要生成的图像内容
	 */
	@JsonProperty("prompt")
	@JsonAlias("prompt")
	private String prompt;

	/**
	 * 负向提示词 描述不希望在图像中出现的内容
	 */
	@JsonProperty("negative_prompt")
	@JsonAlias("negativePrompt")
	private String negativePrompt;

	/**
	 * 图像尺寸 生成图像的分辨率，如：512x512、1024x1024等
	 */
	@JsonProperty("image_size")
	@JsonAlias("imageSize")
	private String imageSize;

	/**
	 * 批次大小 一次生成的图像数量
	 */
	@JsonProperty("batch_size")
	@JsonAlias("batchSize")
	private int batchSize;

	/**
	 * 随机种子 用于生成可重复的结果，相同种子会生成相同的图像
	 */
	@JsonProperty("seed")
	@JsonAlias("seed")
	private Long seed;

	/**
	 * 推理步数 生成过程的迭代次数，步数越多质量越高但耗时越长
	 */
	@JsonProperty("num_inference_steps")
	@JsonAlias("numInferenceSteps")
	private int numInferenceSteps;

	/**
	 * 引导系数 控制生成图像与提示词的匹配程度，值越高越接近提示词
	 */
	@JsonProperty("guidance_scale")
	@JsonAlias("guidanceScale")
	private double guidanceScale;

	/**
	 * 参考图像 Base64编码的图像数据，用于图生图任务
	 */
	@JsonProperty("image")
	@JsonAlias("image")
	private String image;

	/**
	 * 参考图像 Base64编码的图像数据，用于图生图任务
	 */
	@JsonProperty("images")
	@JsonAlias("images")
	private List<String> images;

	/**
	 * 模型名称 指定使用的图像生成模型
	 */
	@JsonProperty("model")
	@JsonAlias("model")
	private String model;

}
