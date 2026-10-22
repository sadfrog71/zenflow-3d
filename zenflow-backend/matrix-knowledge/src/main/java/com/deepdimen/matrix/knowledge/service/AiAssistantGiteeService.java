package com.deepdimen.matrix.knowledge.service;

import com.deepdimen.matrix.knowledge.dto.GiteeAiAudioTranscriptionDTO;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Gitee AI服务接口 集成Gitee AI平台的音频识别能力 <a href="https://ai.gitee.com/">Gitee AI官网</a>
 *
 * @author caoyuan
 * @date 2025/11/04
 */
public interface AiAssistantGiteeService {

	/**
	 * 音频转文本 将音频文件转换为文本内容，支持多种语言识别
	 * @param file 音频文件，支持mp3、wav、m4a等格式
	 * @param model 使用的语音识别模型名称
	 * @return 返回识别后的文本内容和相关元数据
	 */
	@PostExchange(value = "/audio/transcriptions", contentType = "multipart/form-data")
	GiteeAiAudioTranscriptionDTO.Response audioTranscription(@RequestPart("file") MultipartFile file,
			@RequestPart("model") String model);

}
