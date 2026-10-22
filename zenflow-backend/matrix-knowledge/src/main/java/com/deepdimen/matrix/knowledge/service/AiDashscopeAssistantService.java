package com.deepdimen.matrix.knowledge.service;

import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;

import java.util.List;

/**
 * 阿里云百炼AI特殊模型服务接口 集成阿里云通义系列模型的图像、语音和多模态能力
 * <a href="https://bailian.console.aliyun.com/">百炼平台</a>
 *
 * @author caoyuan
 * @date 2024/09/27
 */
public interface AiDashscopeAssistantService {

	/**
	 * AI图像生成 使用通义万相等模型根据文本描述生成图像
	 * @param prompt 图像描述提示词，越详细生成效果越好
	 * @param aiModelEntity AI模型配置实体，包含API密钥和模型参数
	 * @return 生成图像的URL地址或Base64编码
	 */
	String generateImage(String prompt, AiModelEntity aiModelEntity);

	/**
	 * 音频转文本（语音识别） 将音频文件转换为文本内容，支持多种音频格式
	 * @param fileData 音频文件的字节数组
	 * @param aiModelEntity AI模型配置实体，包含语音识别模型信息
	 * @return 识别出的文本内容
	 */
	String audioToText(byte[] fileData, AiModelEntity aiModelEntity);

	/**
	 * 文本转音频（语音合成） 将文本内容合成为自然语音音频
	 * @param text 需要合成语音的文本内容
	 * @return 合成音频的URL地址或Base64编码
	 */
	String textToAudio(String text);

	/**
	 * 多模态向量嵌入 将图像、音频等多模态内容转换为统一的向量表示，用于相似度检索和语义理解
	 * @param base64 Base64编码的文件内容（图像、音频等）
	 * @param fileType 文件类型（image/png、audio/wav等）
	 * @return 多模态嵌入向量，用于向量数据库存储和检索
	 * @throws NoApiKeyException 未配置有效的API密钥时抛出
	 * @throws UploadFileException 文件上传或处理失败时抛出
	 */
	List<Double> multimodal(String base64, String fileType) throws NoApiKeyException, UploadFileException;

}
