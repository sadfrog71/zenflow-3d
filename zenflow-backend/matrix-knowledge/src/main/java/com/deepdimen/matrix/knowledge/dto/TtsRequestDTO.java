package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

/**
 * TTS（文本转语音）请求数据传输对象 用于封装文本转语音服务的请求参数
 *
 * @author caoyuan
 * @date 2024/09/03
 */
@Data
public class TtsRequestDTO {

	/**
	 * 应用配置信息
	 */
	private App app = new App();

	/**
	 * 用户信息
	 */
	private User user = new User();

	/**
	 * 音频配置信息
	 */
	private Audio audio = new Audio();

	/**
	 * 请求参数信息
	 */
	private Request request = new Request();

	/**
	 * 构造函数
	 * @param text 需要转换为语音的文本内容
	 */
	public TtsRequestDTO(String text) {
		this.request.text = text;
	}

	/**
	 * 应用配置内部类 封装TTS服务的应用认证信息
	 */
	@Data
	public class App {

		/**
		 * 应用ID TTS服务的应用标识符
		 */
		private String appid;

		/**
		 * 认证令牌 用于验证请求的合法性
		 */
		private String token;

		/**
		 * 集群标识 指定使用的TTS服务集群
		 */
		private String cluster;

	}

	/**
	 * 用户信息内部类 封装请求用户的标识信息
	 */
	@Data
	public class User {

		/**
		 * 用户ID 请求用户的唯一标识符
		 */
		private String uid;

	}

	/**
	 * 音频配置内部类 封装生成音频的相关参数
	 */
	@Data
	public class Audio {

		/**
		 * 语音类型 指定使用的语音模型或说话人
		 */
		private String voice_type;

		/**
		 * 编码格式 指定输出音频的编码格式，如mp3、wav等
		 */
		private String encoding;

		/**
		 * 语速比例 控制语音播放速度，1.0为正常速度
		 */
		private float speed_ratio;

		/**
		 * 音量比例 控制音频音量大小，1.0为正常音量
		 */
		private float volume_ratio;

		/**
		 * 音调比例 控制语音音调高低，1.0为正常音调
		 */
		private float pitch_ratio;

		/**
		 * 情感类型 指定语音的情感表达方式
		 */
		private String emotion;

	}

	/**
	 * 请求参数内部类 封装TTS请求的核心参数
	 *
	 * @author caoyuan
	 * @date 2024/09/03
	 */
	@Data
	public class Request {

		/**
		 * 请求ID 用于追踪和标识本次请求
		 */
		private String reqid;

		/**
		 * 待转换的文本内容 需要转换为语音的文本
		 */
		private String text;

		/**
		 * 文本类型 默认为"plain"，表示纯文本
		 */
		private String text_type = "plain";

		/**
		 * 操作类型 默认为"query"，表示查询操作
		 */
		private String operation = "query";

	}

}
