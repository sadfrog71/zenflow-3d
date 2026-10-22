package com.deepdimen.matrix.knowledge.config.properties;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

/**
 * Siliconflow AI平台配置
 *
 * @author caoyuan
 * @date 2024/6/18
 * <p>
 */
@Data
@FieldNameConstants
public class SiliconflowProperties {

	/**
	 * 文生图API地址
	 */
	private String imageUrl = "https://api.siliconflow.cn/v1/black-forest-labs/FLUX.1-schnell/text-to-image";

	/**
	 * 语音转文字API地址
	 */
	private String audioUrl = "https://api.siliconflow.cn/v1/audio/transcriptions";

	/**
	 * 语音识别模型
	 */
	private String audioModel = "iic/SenseVoiceSmall";

	/**
	 * API访问密钥
	 */
	private String apikey;

	/**
	 * 图片生成提示词
	 */
	private String prompt;

	/**
	 * 生成图片尺寸
	 */
	private String image_size = "1024x1024";

	/**
	 * 批次大小
	 */
	private int batch_size = 1;

	/**
	 * 推理步数
	 */
	private int num_inference_steps = 20;

	/**
	 * 随机种子
	 */
	private String seed;

}
