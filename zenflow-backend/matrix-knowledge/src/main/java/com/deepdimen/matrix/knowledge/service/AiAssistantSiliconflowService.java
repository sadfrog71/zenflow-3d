package com.deepdimen.matrix.knowledge.service;

import com.deepdimen.matrix.knowledge.dto.AiImageCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.AiVideoCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.AiVideoGetCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.AiVoiceCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.RerankerModelDTO;
import com.deepdimen.matrix.knowledge.dto.SiliconflowImageModelDTO;
import com.deepdimen.matrix.knowledge.dto.SiliconflowVideoModelDTO;
import com.deepdimen.matrix.knowledge.dto.*;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Siliconflow AI特殊模型服务接口 集成Siliconflow平台的多模态AI能力，包括图像生成、音频处理、视频生成等功能
 * <a href="https://cloud.siliconflow.cn/">Siliconflow官网</a>
 *
 * @author caoyuan
 * @date 2024/09/27
 */
public interface AiAssistantSiliconflowService {

	/**
	 * 生成AI图像 根据文本描述生成对应的图像内容
	 * @param request 图像生成请求对象，包含提示词、图像尺寸、风格等参数
	 * @return 返回生成的图像信息，包含图像URL和元数据
	 */
	@PostExchange("/image/generations")
	SiliconflowImageModelDTO.ImageModelResponse generateImage(@RequestBody AiImageCompletionsDTO request);

	/**
	 * 语音转文本 将音频文件转换为文本内容，支持多种语言识别
	 * @param file 音频文件，支持mp3、wav等格式
	 * @param model 使用的语音识别模型名称
	 * @return 返回识别后的文本内容
	 */
	@PostExchange(value = "/audio/transcriptions")
	String audioToText(@RequestPart MultipartFile file, @RequestParam String model);

	/**
	 * 文本重排序 对检索结果进行重新排序，提高相关性排名的准确度
	 * @param request 重排序请求对象，包含查询语句和待排序的文档列表
	 * @return 返回重新排序后的结果，包含相关性分数
	 */
	@PostExchange(value = "/rerank")
	RerankerModelDTO.RerankerModelResponse rerank(@RequestBody RerankerModelDTO.RerankerModelRequest request);

	/**
	 * 文本转语音 将文本内容转换为语音音频文件
	 * @param audioModelDTO 语音合成配置对象，包含文本内容、语音风格、语速等参数
	 * @return 返回生成的音频资源文件
	 */
	@PostExchange(value = "/audio/speech")
	Resource textToAudio(@RequestBody AiVoiceCompletionsDTO audioModelDTO);

	/**
	 * 生成AI视频 根据文本描述或图像生成视频内容
	 * @param aiVideoCompletionsDTO 视频生成请求对象，包含提示词、视频时长、分辨率等参数
	 * @return 返回视频生成任务的响应信息，包含任务ID
	 */
	@PostExchange(value = "/video/submit")
	SiliconflowVideoModelDTO.VideoRequestModelResponse generateVideo(
			@RequestBody AiVideoCompletionsDTO aiVideoCompletionsDTO);

	/**
	 * 查询视频生成状态 根据任务ID查询视频生成的进度和状态信息
	 * @param aiVideoGetCompletionsDTO 包含视频任务ID的查询请求对象
	 * @return 返回视频生成状态信息，包含进度、视频URL等
	 */
	@PostExchange(value = "/video/status")
	SiliconflowVideoModelDTO.VideoGetModelResponse getVideo(
			@RequestBody AiVideoGetCompletionsDTO aiVideoGetCompletionsDTO);

}
