package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.knowledge.dto.AiGenerateDTO;
import com.deepdimen.matrix.knowledge.dto.AiVoiceCompletionsDTO;
import com.deepdimen.matrix.knowledge.dto.VoiceTranscriptionDTO;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.mapper.AiModelMapper;
import com.deepdimen.matrix.knowledge.service.AiAssistantSiliconflowService;
import com.deepdimen.matrix.knowledge.service.AiDashscopeAssistantService;
import com.deepdimen.matrix.knowledge.service.AiGenerateService;
import com.deepdimen.matrix.knowledge.support.constant.ModelSupportEnums;
import com.deepdimen.matrix.knowledge.support.constant.ModelTypeEnums;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.speech.PcmWavValidator;
import com.deepdimen.matrix.knowledge.support.speech.SpeechCorrelationId;
import com.deepdimen.matrix.knowledge.support.speech.SpeechRecognitionAdapterRegistry;
import com.deepdimen.matrix.knowledge.support.speech.SpeechRecognitionException;
import com.deepdimen.matrix.knowledge.support.speech.SpeechRecognitionSupport;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AI内容生成服务实现类
 * <p>
 * 提供文本和音频的相互转换功能 支持多种模型进行音频识别和语音合成 使用提示词模板生成结构化文本
 *
 * @author caoyuan
 * @date 2024/6/17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGenerateServiceImpl implements AiGenerateService {

	/**
	 * 阿里云AI助手服务 用于音频转文本备选方案
	 */
	private final AiDashscopeAssistantService aiDashscopeAssistantService;

	/**
	 * AI模型数据访问层
	 */
	private final AiModelMapper aiModelMapper;

	/**
	 * 模型提供者 管理多种模型实例
	 */
	private final ModelProvider modelProvider;

	private final SpeechRecognitionAdapterRegistry speechRecognitionAdapterRegistry;

	/**
	 * 文本生成提示词模板 用于构建结构化提示词
	 */
	@Value("classpath:/prompts/gen-text.st")
	private Resource generateTextResource;

	/**
	 * 生成结构化文本
	 * <p>
	 * 使用提示词模板和条件生成文本 支持动态指定AI模型
	 * @param generateDTO 包含提示词和条件的输入参数
	 * @return 生成的文本内容
	 */
	@Override
	public String generateText(AiGenerateDTO generateDTO) {
		PromptTemplate promptTemplat = new PromptTemplate(generateTextResource);
		promptTemplat.add("prompt", generateDTO.getPrompt());
		promptTemplat.add("conditions", generateDTO.getConditions());

		return modelProvider.getAiAssistant(generateDTO.getModelName()).getValue().chat(promptTemplat.render());
	}

	/**
	 * 选择功能（未实现）
	 * @param conversationId 会话ID
	 * @param description 描述
	 * @return null
	 */
	@Override
	public String selectFunction(String conversationId, String description) {
		return null;
	}

	/**
	 * 音频转文本
	 * <p>
	 * 使用当前租户的默认 ASR 模型，通过 Provider 适配器将音频识别为文本
	 * @param fileData 音频文件字节数组
	 * @return 识别后的文本内容
	 */
	@Override
	public String generateTextByAudio(byte[] fileData, String traceId) {
		if (fileData == null || fileData.length == 0) {
			throw new SpeechRecognitionException("AUDIO_EMPTY", "音频内容不能为空");
		}
		PcmWavValidator.validate(fileData);
		List<AiModelEntity> modelEntityList = aiModelMapper.selectList(Wrappers.<AiModelEntity>lambdaQuery()
			.eq(AiModelEntity::getModelType, ModelTypeEnums.VOICE.getType())
			.eq(AiModelEntity::getDefaultModel, YesNoEnum.YES.getCode())
			.isNotNull(AiModelEntity::getExtData));

		List<AiModelEntity> defaultAsrModels = modelEntityList.stream()
			.filter(aiModelEntity -> YesNoEnum.YES.getCode().equals(aiModelEntity.getDefaultModel()))
			.filter(aiModelEntity -> YesNoEnum.YES.getCode().equals(SpeechRecognitionSupport.voiceType(aiModelEntity)))
			.toList();

		if (defaultAsrModels.isEmpty()) {
			throw new SpeechRecognitionException("ASR_MODEL_NOT_CONFIGURED", "当前租户未配置默认语音识别模型");
		}
		if (defaultAsrModels.size() > 1) {
			throw new SpeechRecognitionException("ASR_MODEL_CONFLICT", "当前租户存在多个默认语音识别模型");
		}

		VoiceTranscriptionDTO transcription = speechRecognitionAdapterRegistry.transcribe(fileData,
				defaultAsrModels.get(0));
		transcription.setTraceId(SpeechCorrelationId.resolve(traceId));
		return JSONUtil.toJsonStr(transcription);
	}

	/**
	 * 文本转音频
	 * <p>
	 * 使用GPT-SoVITS模型进行语音合成 返回Base64编码的音频数据
	 * @param text 待合成的文本内容
	 * @return Base64编码的音频数据
	 */
	@Override
	@SneakyThrows
	public String generateAudioByText(String text) {
		List<AiModelEntity> modelEntityList = aiModelMapper.selectList(Wrappers.<AiModelEntity>lambdaQuery()
			.eq(AiModelEntity::getProvider, ModelSupportEnums.SILICONFLOW_SENSE_VOICE_SMALL.getProvider())
			.eq(AiModelEntity::getModelType, ModelTypeEnums.VOICE.getType())
			.isNotNull(AiModelEntity::getExtData));

		if (Objects.isNull(modelEntityList)) {
			return "未找到对应的音频模型";
		}

		Optional<AiModelEntity> voiceOptional = modelEntityList.stream()
			.filter(aiModelEntity -> JSONUtil.isTypeJSON(aiModelEntity.getExtData())
					&& YesNoEnum.NO.getCode().equals(JSONUtil.parseObj(aiModelEntity.getExtData()).getStr("voiceType")))
			.findFirst();

		if (voiceOptional.isEmpty()) {
			return "未找到对应的音频模型";
		}
		AiAssistantSiliconflowService fileAssistant = modelProvider
			.getSiliconflowAssistant(voiceOptional.get().getName());

		AiVoiceCompletionsDTO audioModelDTO = new AiVoiceCompletionsDTO();
		audioModelDTO.setInput(text);
		audioModelDTO.setModel(voiceOptional.get().getModelName());

		// timbre
		audioModelDTO.setVoice(StrUtil.format("{}:{}", voiceOptional.get().getModelName(),
				JSONUtil.parseObj(voiceOptional.get().getExtData()).getStr("timbre")));
		Resource resource = fileAssistant.textToAudio(audioModelDTO);
		return Base64.encode(resource.getContentAsByteArray());
	}

}
