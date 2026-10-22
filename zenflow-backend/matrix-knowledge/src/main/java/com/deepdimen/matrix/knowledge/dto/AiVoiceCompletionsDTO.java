package com.deepdimen.matrix.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * AI语音补全数据传输对象
 * <p>
 * 用于封装AI语音合成请求的参数信息
 *
 * @author caoyuan
 * @date 2025/05/08
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY) // 仅当字段非 null 且非 "" 时才序列化
public class AiVoiceCompletionsDTO {

	/**
	 * 输入内容
	 * <p>
	 * 需要转换为语音的文本内容
	 */
	private String input;

	/**
	 * 模型
	 * <p>
	 * 语音合成所使用的AI模型名称
	 */
	private String model;

	/**
	 * 音量增益
	 * <p>
	 * 语音输出的音量增益值，用于调节音量大小
	 */
	private int gain;

	/**
	 * 语速
	 * <p>
	 * 语音播放的速度倍率，1.0为正常速度
	 */
	private double speed = 1.0;

	/**
	 * 语音音色
	 * <p>
	 * 选择的语音音色/发音人标识
	 */
	private String voice;

}
