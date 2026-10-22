package com.deepdimen.matrix.knowledge.service;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.dto.AiEditorCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.AiImageCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.AiImageVisionDTO;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.AiVideoCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.AiVoiceCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI补全服务接口 提供文本、图像、视频和语音等多模态AI生成能力
 *
 * @author caoyuan
 * @date 2025/05/07
 */
public interface AiCompletionsService {

	/**
	 * 文本流式补全 支持编辑器智能补全场景，实时流式返回AI生成的文本内容
	 * @param aiEditorCompletionsDTOList 编辑器补全请求列表，支持批量处理
	 * @return 文本补全结果的响应式流，支持实时推送
	 */
	Flux<AiMessageResultDTO> textCompletions(List<AiEditorCompletionsDTO> aiEditorCompletionsDTOList);

	/**
	 * 图像生成服务 根据文本提示生成相应的图像内容
	 * @param imageCompletionsDTO 图像生成参数，包含提示词、尺寸、风格等配置
	 * @return 包含生成图像URL或Base64数据的响应结果
	 */
	R imageCompletions(AiImageCompletionsDTO imageCompletionsDTO);

	/**
	 * 视频生成服务 根据文本或图像提示生成视频内容，支持异步任务模式
	 * @param aiVideoCompletionsDTO 视频生成参数，包含脚本、时长、分辨率等配置
	 * @return 包含任务ID的响应结果，用于查询生成进度
	 */
	R videoCompletions(AiVideoCompletionsDTO aiVideoCompletionsDTO);

	/**
	 * 查询视频生成状态 获取异步视频生成任务的进度和结果
	 * @param taskId 视频生成任务的唯一标识
	 * @param modeName 模型名称，用于路由到对应的处理服务
	 * @return 包含任务状态、进度和结果URL的响应信息
	 */
	R videoStatus(String taskId, String modeName);

	/**
	 * 语音合成服务 将文本转换为自然语音，支持多种音色和语言
	 * @param aiVoiceCompletionsDTO 语音合成参数，包含文本、音色、语速等配置
	 * @return 包含音频文件URL或Base64数据的响应结果
	 */
	R voiceCompletions(AiVoiceCompletionsDTO aiVoiceCompletionsDTO);

	/**
	 * 获取版本分析结果
	 * @param imageVisionDTO 图像视觉分析数据传输对象
	 * @return 版本分析结果
	 */
	R visionAnalyze(AiImageVisionDTO imageVisionDTO);

}
