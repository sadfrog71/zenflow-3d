package com.deepdimen.matrix.knowledge.controller;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.dto.AiEditorCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.AiImageCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.AiImageVisionDTO;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.AiVideoCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.AiVoiceCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.*;
import com.deepdimen.matrix.knowledge.service.AiCompletionsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI内容生成控制器 提供多种AI内容生成服务，包括文本补全、图像生成、视频生成、语音合成等功能 主要用于支持AI编辑器和各类内容创作场景
 *
 * @author caoyuan
 * @date 2025/05/07
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/completions")
@Tag(description = "completions", name = "AI内容生成控制器")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiCompletionsController {

	/**
	 * AI内容生成服务，处理各类内容生成请求
	 */
	private final AiCompletionsService completionsService;

	/**
	 * 文本补全接口 接收编辑器的上下文信息，生成智能代码或文本补全建议 支持流式输出，实时返回生成内容
	 * @param aiEditorCompletionsDTOList 编辑器上下文列表，包含当前光标位置前后的文本
	 * @return 流式返回补全建议内容
	 */
	@PostMapping("/text")
	public Flux<AiMessageResultDTO> text(@RequestBody List<AiEditorCompletionsDTO> aiEditorCompletionsDTOList) {
		return completionsService.textCompletions(aiEditorCompletionsDTOList);
	}

	/**
	 * 图像生成接口 根据文本描述生成对应的图像内容 支持多种图像风格和尺寸设置
	 * @param aiImageCompletionsDTO 图像生成参数，包含描述文本、风格、尺寸等
	 * @return 生成的图像结果，包含图像URL或Base64编码
	 */
	@PostMapping("/image")
	public R image(@RequestBody AiImageCompletionsDTO aiImageCompletionsDTO) {
		return completionsService.imageCompletions(aiImageCompletionsDTO);
	}

	/**
	 * 视频生成接口 根据文本描述或图像生成视频内容 支持异步处理，返回任务ID供后续查询
	 * @param aiVideoCompletionsDTO 视频生成参数，包含描述、时长、分辨率等
	 * @return 视频生成任务信息，包含任务ID
	 */
	@PostMapping("/video")
	public R video(@RequestBody AiVideoCompletionsDTO aiVideoCompletionsDTO) {
		return completionsService.videoCompletions(aiVideoCompletionsDTO);
	}

	/**
	 * 查询视频生成状态 根据任务ID查询视频生成的进度和结果
	 * @param requestId 视频生成任务的唯一标识
	 * @param modelName 使用的模型名称
	 * @return 视频生成状态，包含进度、状态、结果URL等
	 */
	@GetMapping("/video/status")
	public R status(String requestId, String modelName) {
		return completionsService.videoStatus(requestId, modelName);
	}

	/**
	 * 语音合成接口 将文本内容转换为自然语音 支持多种语言、音色和语速设置
	 * @param aiVoiceCompletionsDTO 语音合成参数，包含文本、音色、语速等
	 * @return 合成的语音文件信息
	 */
	@PostMapping("/voice")
	public R voice(@RequestBody AiVoiceCompletionsDTO aiVoiceCompletionsDTO) {
		return completionsService.voiceCompletions(aiVoiceCompletionsDTO);
	}

	/**
	 * 分析图片
	 * @param imageVisionDTO 图片视觉分析数据传输对象
	 * @return 包含分析结果的响应对象
	 */
	@PostMapping("/image/analyze")
	public R analyzeImage(@RequestBody AiImageVisionDTO imageVisionDTO) {
		return completionsService.visionAnalyze(imageVisionDTO);
	}

}
