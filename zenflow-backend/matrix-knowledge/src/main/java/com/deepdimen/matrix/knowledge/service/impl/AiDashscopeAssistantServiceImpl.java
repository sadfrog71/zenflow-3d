package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.json.JSONUtil;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.embeddings.*;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.core.exception.CheckedException;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.mapper.AiModelMapper;
import com.deepdimen.matrix.knowledge.service.AiDashscopeAssistantService;
import com.deepdimen.matrix.knowledge.support.constant.ImageTaskStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.ModelSupportEnums;
import com.deepdimen.matrix.knowledge.support.constant.ModelTypeEnums;
import com.deepdimen.matrix.knowledge.support.constant.MultimodalModelEnums;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 阿里云达摩院AI服务实现类
 * <p>
 * 封装阿里云百炼平台的AI能力 提供图像生成、语音合成、多模态向量化等功能 支持文本、图像、音频、视频等多种模态处理
 *
 * @author caoyuan
 * @date 2024/10/12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDashscopeAssistantServiceImpl implements AiDashscopeAssistantService {

	/**
	 * AI模型数据访问层
	 */
	private final AiModelMapper aiModelMapper;

	/**
	 * 使用阿里云模型生成图像
	 * <p>
	 * 调用达摩院图像合成服务 自动添加生成限制条件以确保质量
	 * @param prompt 图像生成提示词
	 * @param aiModelEntity 模型配置信息
	 * @return 生成的图像URL，失败返回null
	 */
	@Override
	@SneakyThrows
	public String generateImage(String prompt, AiModelEntity aiModelEntity) {
		ImageSynthesis is = new ImageSynthesis();
		ImageSynthesisParam param = ImageSynthesisParam.builder()
			.apiKey(aiModelEntity.getApiKey())
			.model(aiModelEntity.getModelName())
			.prompt(prompt + PromptBuilder.render("gen-image-limit.st", Map.of()))
			.build();

		ImageSynthesisResult result = is.call(param);
		if (ImageTaskStatusEnums.SUCCESSED.getCode().equals(result.getOutput().getTaskStatus())) {
			return result.getOutput().getResults().get(0).get("url");
		}

		log.error("阿里云大模型 {} 生成图片失败:{}", aiModelEntity.getModelName(), result.getOutput().getMessage());
		return null;
	}

	/**
	 * 音频转文本（语音识别）
	 * <p>
	 * 目前暂未实现该功能
	 * @param fileData 音频文件字节数据
	 * @param aiModelEntity 模型配置信息
	 * @return 识别的文本内容
	 */
	@Override
	public String audioToText(byte[] fileData, AiModelEntity aiModelEntity) {
		throw new UnsupportedOperationException("暂不支持");
	}

	/**
	 * 文本转语音合成
	 * <p>
	 * 使用阿里云CosyVoice模型生成语音 支持多种音色选择，默认使用longcheng音色
	 * @param text 待合成的文本内容
	 * @return Base64编码的音频数据
	 */
	@Override
	public String textToAudio(String text) {
		AiModelEntity aiModelEntity = aiModelMapper.selectOne(Wrappers.<AiModelEntity>lambdaQuery()
			.eq(AiModelEntity::getModelName, ModelSupportEnums.ALIYUN_COSYVOICE_V1.getCode()), false);

		if (Objects.isNull(aiModelEntity)) {
			throw new CheckedException("未找到对应的音频模型:" + ModelSupportEnums.ALIYUN_COSYVOICE_V1.getCode());
		}

		String voice = JSONUtil.parseObj(aiModelEntity.getExtData()).getStr("voice", "longcheng");
		SpeechSynthesisParam param = SpeechSynthesisParam.builder()
			.apiKey(aiModelEntity.getApiKey())
			.model(aiModelEntity.getModelName())
			.voice(voice)
			.build();
		SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, null);
		ByteBuffer audio = synthesizer.call(text);
		return Base64.encode(audio.array());
	}

	/**
	 * 多模态内容向量化
	 * <p>
	 * 支持文本、图像、音频、视频等多种模态的向量化处理 支持阿里云所有多模态向量模型： - multimodal-embedding-v1 -
	 * tongyi-embedding-vision-plus - tongyi-embedding-vision-flash
	 * 用于多模态内容的相似度检索和匹配，文本直接传入，其他类型需要Base64编码
	 * @param base64 文本内容或Base64编码的媒体内容
	 * @param fileType 文件类型：Chat(文本)/Image/Voice/Video
	 * @return 向量化结果的Double列表
	 * @throws NoApiKeyException API密钥无效时抛出
	 * @throws UploadFileException 文件上传失败时抛出
	 * @throws CheckedException 模型不存在时抛出
	 */
	@Override
	public List<Double> multimodal(String base64, String fileType) throws NoApiKeyException, UploadFileException {
		// 查询所有阿里云多模态向量模型，优先使用已配置的模型
		AiModelEntity aiModelEntity = aiModelMapper.selectOne(Wrappers.<AiModelEntity>lambdaQuery()
			.in(AiModelEntity::getModelName, MultimodalModelEnums.ALIYUN_MULTIMODAL_V1.getCode(),
					MultimodalModelEnums.ALIYUN_TONGYI_EMBEDDING_VISION_PLUS.getCode(),
					MultimodalModelEnums.ALIYUN_TONGYI_EMBEDDING_VISION_FLASH.getCode()),
				false);

		if (Objects.isNull(aiModelEntity)) {
			throw new CheckedException(
					"未找到任何已配置的阿里云多模态向量模型，支持的模型: " + MultimodalModelEnums.ALIYUN_MULTIMODAL_V1.getCode() + ", "
							+ MultimodalModelEnums.ALIYUN_TONGYI_EMBEDDING_VISION_PLUS.getCode() + ", "
							+ MultimodalModelEnums.ALIYUN_TONGYI_EMBEDDING_VISION_FLASH.getCode());
		}

		List<MultiModalEmbeddingItemBase> fileList = new ArrayList<>();

		// 文本类型
		if (ModelTypeEnums.CHAT.getType().equals(fileType)) {
			fileList.add(new MultiModalEmbeddingItemText(base64));
		}

		// 图片类型
		if (ModelTypeEnums.IMAGE.getType().equals(fileType)) {
			fileList.add(new MultiModalEmbeddingItemImage(base64));
		}

		// 音频类型
		if (ModelTypeEnums.VOICE.getType().equals(fileType)) {
			fileList.add(new MultiModalEmbeddingItemAudio(base64));
		}

		// 视频类型
		if (ModelTypeEnums.VIDEO.getType().equals(fileType)) {
			fileList.add(new MultiModalEmbeddingItemVideo(base64));
		}

		MultiModalEmbeddingParam param = MultiModalEmbeddingParam.builder()
			.apiKey(aiModelEntity.getApiKey())
			.model(aiModelEntity.getModelName())
			.contents(fileList)
			.build();

		// 多模态嵌入调用
		MultiModalEmbedding embedding = new MultiModalEmbedding();
		MultiModalEmbeddingResult multiModalEmbeddingResult = embedding.call(param);
		return multiModalEmbeddingResult.getOutput().getEmbeddings().get(0).getEmbedding();
	}

}
