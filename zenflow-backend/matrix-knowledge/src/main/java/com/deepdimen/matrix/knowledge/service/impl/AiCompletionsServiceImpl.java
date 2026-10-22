package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.feign.RemoteFileService;
import com.deepdimen.matrix.admin.api.utils.ByteArrayMultipartFile;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.dto.AiEditorCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.AiImageArkCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.AiImageCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.AiImageVisionDTO;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.AiVideoCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.AiVideoGetCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.AiVoiceCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.OpenRouterImageModelDTO;
import com.deepdimen.matrix.knowledge.dto.SiliconflowImageModelDTO;
import com.deepdimen.matrix.knowledge.dto.SiliconflowVideoModelDTO;
import com.deepdimen.matrix.knowledge.service.AiAssistantArkService;
import com.deepdimen.matrix.knowledge.service.AiAssistantOpenRouterService;
import com.deepdimen.matrix.knowledge.service.AiAssistantSiliconflowService;
import com.deepdimen.matrix.knowledge.service.AiCompletionsService;
import com.deepdimen.matrix.knowledge.service.AiDashscopeAssistantService;
import com.deepdimen.matrix.knowledge.service.AiNoMemoryStreamAssistantService;
import com.deepdimen.matrix.knowledge.dto.*;
import com.deepdimen.matrix.knowledge.entity.AiMaterialLogEntity;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.mapper.AiMaterialLogMapper;
import com.deepdimen.matrix.knowledge.mapper.AiModelMapper;
import com.deepdimen.matrix.knowledge.service.*;
import com.deepdimen.matrix.knowledge.support.constant.MaterialStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.ModelSupportEnums;
import com.deepdimen.matrix.knowledge.support.constant.ModelTypeEnums;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.util.JSONRepairUtil;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.internal.chat.Content;
import dev.langchain4j.model.openai.internal.chat.ContentType;
import dev.langchain4j.model.openai.internal.chat.ImageUrl;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import reactor.core.publisher.Flux;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI多模态补全服务实现类
 * <p>
 * 提供文本、图像、音频、视频等多模态内容生成服务 支持多种AI模型和供应商集成 实现流式响应和资源管理功能
 *
 * @author caoyuan
 * @date 2025/05/07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCompletionsServiceImpl implements AiCompletionsService {

	/**
	 * 达摩院AI助手服务 用于图像生成
	 */
	private final AiDashscopeAssistantService aiDashscopeAssistantService;

	/**
	 * 远程文件服务 用于保存生成的媒体资源
	 */
	private final RemoteFileService remoteFileService;

	/**
	 * 模型提供者 管理各种AI模型实例
	 */
	private final ModelProvider modelProvider;

	/**
	 * AI模型数据访问层
	 */
	private final AiModelMapper aiModelMapper;

	/**
	 * 素材日志数据访问层 记录生成的媒体资源信息
	 */
	private final AiMaterialLogMapper materialLogMapper;

	/**
	 * 文本内容补全
	 * <p>
	 * 从消息列表中提取用户消息并生成补全内容 使用默认聊天模型进行流式响应
	 * @param aiEditorCompletionsDTOList 包含角色和内容的消息列表
	 * @return 流式响应的AI生成结果
	 */
	@Override
	public Flux<AiMessageResultDTO> textCompletions(List<AiEditorCompletionsDTO> aiEditorCompletionsDTOList) {
		AiModelEntity aiModel = aiModelMapper.selectOne(Wrappers.<AiModelEntity>lambdaQuery()
			.eq(AiModelEntity::getModelType, ModelTypeEnums.CHAT.getType())
			.eq(AiModelEntity::getDefaultModel, YesNoEnum.YES.getCode()), false);

		if (Objects.isNull(aiModel)) {
			return Flux.just(new AiMessageResultDTO("请先设置默认聊天模型"));
		}

		// 提取用户角色的消息，用于生成补全内容
		List<AiEditorCompletionsDTO> userMessages = aiEditorCompletionsDTOList.stream()
			.filter(message -> StrUtil.equalsAnyIgnoreCase(ChatMessageType.USER.name(), message.getRole()))
			.toList();

		List<ChatMessage> chatMessageList = new ArrayList<>();
		if (!userMessages.isEmpty()) {
			chatMessageList.add(UserMessage.from(userMessages.get(0).getContent())); // 第一个用户消息
			if (userMessages.size() > 1) {
				chatMessageList.add(UserMessage.from(userMessages.get(userMessages.size() - 1).getContent())); // 最后一个用户消息
			}
		}

		Pair<StreamingChatModel, AiNoMemoryStreamAssistantService> aiStreamAssistantServicePair = modelProvider
			.getAiNoMemoryStreamAssistant(aiModel.getName());
		return Flux.<String>create(emitter -> {
			aiStreamAssistantServicePair.getKey().chat(chatMessageList, new StreamingChatResponseHandler() {
				@Override
				public void onPartialResponse(String partialResponse) {
					emitter.next(partialResponse);
				}

				@Override
				public void onCompleteResponse(ChatResponse completeResponse) {
					emitter.complete();
				}

				@Override
				public void onError(Throwable error) {
					emitter.error(error);
				}
			});
		}).map(AiMessageResultDTO::new);
	}

	/**
	 * AI图像生成
	 * <p>
	 * 使用AI模型优化提示词后生成图像 支持多种图像生成模型（硅基流动、达摩院等） 自动下载并保存生成的图像资源
	 * @param imageCompletionsDTO 包含提示词和模型配置的请求参数
	 * @return 生成的图像URL列表
	 */
	@Override
	public R<List<String>> imageCompletions(AiImageCompletionsDTO imageCompletionsDTO) {

		AiModelEntity aiModelEntity = aiModelMapper.selectOne(
				Wrappers.<AiModelEntity>lambdaQuery().eq(AiModelEntity::getName, imageCompletionsDTO.getModel()));

		if (aiModelEntity == null) {
			return R.failed("图片模型不存在");
		}

		// 使用聊天模型优化用户提交的提示词
		ChatModel chatModel = modelProvider.getAiAssistant().getKey();
		String resultPrompt = chatModel
			.chat(PromptBuilder.render("gen-image.st", Map.of("input", imageCompletionsDTO.getPrompt())))
				+ PromptBuilder.render("gen-image-limit.st",
						Map.of(AiImageCompletionsDTO.Fields.imageSize, imageCompletionsDTO.getImageSize(),
								AiImageCompletionsDTO.Fields.negativePrompt,
								Objects.requireNonNullElse(imageCompletionsDTO.getNegativePrompt(), StrUtil.EMPTY)));

		// 使用硅基流动模型生成图片
		if (aiModelEntity.getProvider().equals(ModelSupportEnums.SILICONFLOW_KWAI_KOLORS_KOLORS.getProvider())) {
			return siliconflowImage(imageCompletionsDTO, aiModelEntity, resultPrompt);
		}

		// 使用OpenRouter Gemini 2.5 Flash Image Preview模型生成图片
		if (aiModelEntity.getProvider()
			.equals(ModelSupportEnums.OPEN_ROUTER_GEMINI_2_5_FLASH_IMAGE_PREVIEW.getProvider())) {
			return openrouterImage(imageCompletionsDTO, aiModelEntity, resultPrompt);
		}

		if (aiModelEntity.getProvider().equals(ModelSupportEnums.ARK_DOUBAO_SEEDREAM4.getProvider())) {
			return arkImage(imageCompletionsDTO, aiModelEntity, resultPrompt);
		}

		return R.failed("生成图片失败");
	}

	/**
	 * 生成ARK图片
	 * @param imageCompletionsDTO 图片生成请求DTO
	 * @param aiModelEntity AI模型实体
	 * @param resultPrompt 结果提示语
	 * @return 包含图片URL列表的响应结果
	 */
	private R<List<String>> arkImage(AiImageCompletionsDTO imageCompletionsDTO, AiModelEntity aiModelEntity,
			String resultPrompt) {

		AiAssistantArkService arkAssistant = modelProvider.getArkAssistant(imageCompletionsDTO.getModel());

		AiImageArkCompletionsDTO.Request request = new AiImageArkCompletionsDTO.Request();
		request.setModel(aiModelEntity.getModelName());
		request.setPrompt(resultPrompt);
		request.setResponseFormat("url");
		request.setWatermark(false);
		request.setImage(imageCompletionsDTO.getImage());
		request.setSequentialImageGeneration("disabled");
		AiImageArkCompletionsDTO.Response response = arkAssistant.generateImage(request);
		List<AiImageArkCompletionsDTO.Image> responseData = response.getData();

		return R.ok(responseData.stream()
			.map(image -> saveRemoteMedia(image.getUrl(), null, ".png", ModelTypeEnums.IMAGE.getType(),
					imageCompletionsDTO.getPrompt(), imageCompletionsDTO.getModel()))
			.toList());
	}

	/**
	 * 通过OpenRouter生成图片
	 * @param imageCompletionsDTO 图片生成请求参数
	 * @param aiModelEntity AI模型实体
	 * @param resultPrompt 最终提示词
	 * @return 包含生成图片URL列表的响应结果
	 * @throws IllegalArgumentException 参数不合法时抛出异常
	 */
	@NotNull
	private R<List<String>> openrouterImage(AiImageCompletionsDTO imageCompletionsDTO, AiModelEntity aiModelEntity,
			String resultPrompt) {
		AiAssistantOpenRouterService fileAssistant = modelProvider
			.getOpenRouterAssistant(imageCompletionsDTO.getModel());

		OpenRouterImageModelDTO.ImageModelRequest modelRequest = new OpenRouterImageModelDTO.ImageModelRequest();
		modelRequest.setModel(aiModelEntity.getModelName());
		modelRequest.setModalities(List.of("image", "text"));

		List<OpenRouterImageModelDTO.ImageUserMessage> userMessageList = new ArrayList<>();

		List<Content> contentList = new ArrayList<>();

		contentList.add(Content.builder().type(ContentType.TEXT).text(resultPrompt).build());
		if (CollUtil.isNotEmpty(imageCompletionsDTO.getImages())) {
			imageCompletionsDTO.getImages()
				.forEach(imageBase64 -> contentList.add(Content.builder()
					.type(ContentType.IMAGE_URL)
					.imageUrl(ImageUrl.builder().url(imageBase64).build())
					.build()));

		}

		userMessageList.add(new OpenRouterImageModelDTO.ImageUserMessage("user", contentList));
		modelRequest.setMessages(userMessageList);

		OpenRouterImageModelDTO.ImageModelResponse modelResponse = fileAssistant.generateImage(modelRequest);

		List<OpenRouterImageModelDTO.Choice> choices = modelResponse.getChoices();
		if (CollUtil.isEmpty(choices)) {
			return R.failed("图片生成失败");
		}

		// 下载第三方图片到本地，避免资源过期
		return R.ok(choices.get(0).getMessage().getImages().stream().map(image -> {
			String base64Img = image.getImageUrl().getUrl();
			int commaIndex = base64Img.indexOf(StrUtil.COMMA);
			String pureBase64 = (commaIndex >= 0) ? base64Img.substring(commaIndex + 1) : base64Img;
			// 使用 Hutool 解码
			byte[] decoded = Base64.decode(pureBase64);
			return saveRemoteMedia(null, decoded, ".png", ModelTypeEnums.IMAGE.getType(),
					imageCompletionsDTO.getPrompt(), imageCompletionsDTO.getModel());
		}).toList());
	}

	/**
	 * 使用Siliconflow生成图片
	 * @param imageCompletionsDTO 图片生成请求参数
	 * @param aiModelEntity AI模型实体
	 * @param resultPrompt 最终使用的提示词
	 * @return 包含生成的图片URL列表的响应结果
	 * @throws NullPointerException 当参数为null时抛出
	 */
	@NotNull
	private R<List<String>> siliconflowImage(AiImageCompletionsDTO imageCompletionsDTO, AiModelEntity aiModelEntity,
			String resultPrompt) {
		AiAssistantSiliconflowService fileAssistant = modelProvider
			.getSiliconflowAssistant(imageCompletionsDTO.getModel());
		imageCompletionsDTO.setModel(aiModelEntity.getModelName());
		imageCompletionsDTO.setPrompt(resultPrompt);
		SiliconflowImageModelDTO.ImageModelResponse imageResponse = fileAssistant.generateImage(imageCompletionsDTO);
		// 下载第三方图片到本地，避免资源过期
		return R.ok(imageResponse.getImages()
			.stream()
			.map(image -> saveRemoteMedia(image.getUrl(), null, ".png", ModelTypeEnums.IMAGE.getType(),
					imageCompletionsDTO.getPrompt(), imageCompletionsDTO.getModel()))
			.toList());
	}

	/**
	 * AI视频生成
	 * <p>
	 * 使用AI模型生成视频内容 支持异步生成，返回任务ID供后续查询 记录生成任务状态到素材日志表
	 * @param aiVideoCompletionsDTO 包含提示词和模型配置的视频生成请求
	 * @return 视频生成任务ID
	 */
	@Override
	public R videoCompletions(AiVideoCompletionsDTO aiVideoCompletionsDTO) {
		ChatModel chatModel = modelProvider.getAiAssistant().getKey();
		String resultPrompt = chatModel
			.chat(PromptBuilder.render("gen-image.st", Map.of("input", aiVideoCompletionsDTO.getPrompt())));

		AiModelEntity aiModelEntity = aiModelMapper.selectOne(
				Wrappers.<AiModelEntity>lambdaQuery().eq(AiModelEntity::getName, aiVideoCompletionsDTO.getModel()));

		if (aiModelEntity == null) {
			return R.failed("视频模型不存在");
		}

		// 使用硅基流动模型生成视频
		if (aiModelEntity.getProvider().equals(ModelSupportEnums.SILICONFLOW_WAN_2_1_I2V.getProvider())) {
			AiAssistantSiliconflowService fileAssistant = modelProvider
				.getSiliconflowAssistant(aiVideoCompletionsDTO.getModel());
			aiVideoCompletionsDTO.setModel(aiModelEntity.getModelName());
			aiVideoCompletionsDTO.setPrompt(resultPrompt);
			SiliconflowVideoModelDTO.VideoRequestModelResponse videoRequestModelResponse = fileAssistant
				.generateVideo(aiVideoCompletionsDTO);
			// 记录视频生成任务
			AiMaterialLogEntity logEntity = new AiMaterialLogEntity();
			logEntity.setMaterialType(ModelTypeEnums.VIDEO.getType());
			logEntity.setOriginalUrl(videoRequestModelResponse.getRequestId());
			logEntity.setPrompt(resultPrompt);
			logEntity.setMaterialStatus(MaterialStatusEnums.IN_PROGRESS.getStatus());
			logEntity.setModelName(aiModelEntity.getName());
			materialLogMapper.insert(logEntity);
			return R.ok(videoRequestModelResponse.getRequestId());
		}

		return R.failed("视频模型不存在");
	}

	/**
	 * 查询视频生成状态
	 * <p>
	 * 根据任务ID查询视频生成进度 生成完成后自动下载视频文件 更新素材日志记录状态
	 * @param taskId 视频生成任务ID
	 * @param modelName 使用的模型名称
	 * @return 视频生成状态和结果
	 */
	@Override
	public R videoStatus(String taskId, String modelName) {
		AiAssistantSiliconflowService fileAssistant = modelProvider.getSiliconflowAssistant(modelName);

		AiVideoGetCompletionsDTO aiVideoGetCompletionsDTO = new AiVideoGetCompletionsDTO();
		aiVideoGetCompletionsDTO.setRequestId(taskId);
		SiliconflowVideoModelDTO.VideoGetModelResponse modelResponse = fileAssistant.getVideo(aiVideoGetCompletionsDTO);

		AiMaterialLogEntity aiMaterialLog = new AiMaterialLogEntity();
		if (StrUtil.equalsAnyIgnoreCase(MaterialStatusEnums.SUCCEED.getStatus(), modelResponse.getStatus())) {
			// 视频生成成功，下载到本地
			for (SiliconflowVideoModelDTO.Video video : modelResponse.getResults().getVideos()) {
				// 下载视频文件并获取本地URL
				String localURL = saveRemoteMedia(video.getUrl(), null, ".mp4", null, null, null);
				// 更新本地URL到日志记录
				aiMaterialLog.setLocalUrl(localURL);
			}
		}
		aiMaterialLog.setMaterialStatus(modelResponse.getStatus());
		materialLogMapper.update(aiMaterialLog,
				Wrappers.<AiMaterialLogEntity>lambdaQuery().eq(AiMaterialLogEntity::getOriginalUrl, taskId));
		log.info("视频状态查询，任务ID：{}，结果 {}", taskId, modelResponse);
		return R.ok(modelResponse);
	}

	/**
	 * 文本转语音
	 * <p>
	 * 将文本内容转换为语音文件 支持多种语音模型和音色选择 生成的音频文件自动保存到本地
	 * @param aiVoiceCompletionsDTO 包含文本内容、模型和音色配置的请求
	 * @return 生成的音频文件URL
	 */
	@Override
	@SneakyThrows
	public R voiceCompletions(AiVoiceCompletionsDTO aiVoiceCompletionsDTO) {
		AiAssistantSiliconflowService fileAssistant = modelProvider
			.getSiliconflowAssistant(aiVoiceCompletionsDTO.getModel());
		AiModelEntity aiModelEntity = aiModelMapper.selectOne(
				Wrappers.<AiModelEntity>lambdaQuery().eq(AiModelEntity::getName, aiVoiceCompletionsDTO.getModel()));

		if (aiModelEntity == null) {
			return R.failed("模型不存在");
		}

		aiVoiceCompletionsDTO
			.setVoice(StrUtil.format("{}:{}", aiModelEntity.getModelName(), aiVoiceCompletionsDTO.getVoice()));
		aiVoiceCompletionsDTO.setModel(aiModelEntity.getModelName());
		Resource resource = fileAssistant.textToAudio(aiVoiceCompletionsDTO);

		String localURL = saveRemoteMedia(null, resource.getContentAsByteArray(), ".mp3",
				ModelTypeEnums.VOICE.getType(), aiVoiceCompletionsDTO.getInput(), aiVoiceCompletionsDTO.getModel());
		return R.ok(localURL);
	}

	/**
	 * 视觉分析
	 * @param imageVisionDTO 视觉分析DTO
	 * @return 分析结果
	 */
	@Override
	@SneakyThrows
	public R visionAnalyze(AiImageVisionDTO imageVisionDTO) {
		ChatModel chatModel = modelProvider.getAiVisionAssistant().getKey();

		ClassPathResource classPathResource = new ClassPathResource("prompts/ocr-image-security-user-schema.st");

		SystemMessage systemMessage = SystemMessage.from(PromptBuilder.render("ocr-image-security-system.st"));
		UserMessage imageMessage = UserMessage.from(ImageContent.from(imageVisionDTO.getImageBase64()),
				TextContent.from(PromptBuilder.render("ocr-image-security-user.st",
						Map.of(AiImageVisionDTO.Fields.securityLevel, imageVisionDTO.getSecurityLevel()))
						+ StreamUtils.copyToString(classPathResource.getInputStream(), Charset.defaultCharset())));

		ChatResponse response = chatModel.chat(systemMessage, imageMessage);
		return R.ok(JSONRepairUtil.repair(response.aiMessage().text()));
	}

	/**
	 * 保存远程媒体资源到本地
	 * <p>
	 * 下载远程文件或保存字节数组到本地存储 记录素材信息到日志表供后续管理 支持图片、视频、音频等多种媒体类型
	 * @param remoteUrl 远程资源URL（可选）
	 * @param fileByte 文件字节数组（可选）
	 * @param fileExtension 文件扩展名
	 * @param materialType 素材类型
	 * @param prompt 生成时使用的提示词
	 * @param modelName 使用的模型名称
	 * @return 本地存储的文件URL
	 */
	private String saveRemoteMedia(String remoteUrl, byte[] fileByte, String fileExtension, String materialType,
			String prompt, String modelName) {
		// 从远程URL下载文件或使用提供的字节数组
		if (fileByte == null) {
			fileByte = HttpUtil.downloadBytes(remoteUrl);
		}

		R<Map<String, String>> uploadedR = remoteFileService.upload(new ByteArrayMultipartFile(
				StrUtil.format("{}{}", RandomUtil.randomString(6), fileExtension), null, null, fileByte));
		String localURL = MapUtil.getStr(uploadedR.getData(), "url");

		// 需要记录素材时创建日志记录
		if (materialType != null) {
			AiMaterialLogEntity logEntity = new AiMaterialLogEntity();
			logEntity.setMaterialType(materialType);
			logEntity.setOriginalUrl(remoteUrl);
			logEntity.setLocalUrl(localURL);
			logEntity.setMaterialStatus(MaterialStatusEnums.SUCCEED.getStatus());
			if (prompt != null) {
				logEntity.setPrompt(prompt);
			}

			if (modelName != null) {
				logEntity.setModelName(modelName);
			}

			materialLogMapper.insert(logEntity);
		}

		return localURL;
	}

}
