package com.deepdimen.matrix.knowledge.config.properties;

import lombok.Data;

/**
 * 火山引擎语音合成配置
 * 
 * @author caoyuan
 * @date 2024/10/15
 */
@Data
public class VolcengineTtsProperties {

	/**
	 * 语音合成API地址
	 */
	private String apiUrl = "https://openspeech.bytedance.com/api/v1/tts";

	/**
	 * 访问令牌
	 */
	private String accessToken;

	/**
	 * 应用ID
	 */
	private String appId = "4456569928";

	/**
	 * 集群名称
	 */
	private String cluster = "volcano_tts";

	/**
	 * 用户ID
	 */
	private String uid = "388808087185088";

	/**
	 * 语音类型
	 */
	private String voice_type = "BV002";

	/**
	 * 音频编码格式
	 */
	private String encoding = "mp3";

	/**
	 * 语速比例
	 */
	private float speed_ratio = 1;

	/**
	 * 音量比例
	 */
	private float volume_ratio = 1;

	/**
	 * 音调比例
	 */
	private float pitch_ratio = 1;

	/**
	 * 情感类型
	 */
	private String emotion = "happy";

}
