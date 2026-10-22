package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.lang.Pair;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.AiPosterDTO;
import com.deepdimen.matrix.knowledge.entity.AiPosterEntity;
import com.deepdimen.matrix.knowledge.mapper.AiModelMapper;
import com.deepdimen.matrix.knowledge.mapper.AiPosterMapper;
import com.deepdimen.matrix.knowledge.service.AiNoMemoryStreamAssistantService;
import com.deepdimen.matrix.knowledge.service.AiPosterService;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * AI海报模板服务实现类
 * <p>
 * 基于AI模型生成定制化海报内容 支持模板样式和二维码的融合生成 使用流式输出实现实时响应
 *
 * @author matrix
 * @date 2025-04-04 14:25:49
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPosterServiceImpl extends ServiceImpl<AiPosterMapper, AiPosterEntity> implements AiPosterService {

	/**
	 * 模型提供者 管理AI模型实例
	 */
	private final ModelProvider modelProvider;

	/**
	 * AI模型数据访问层
	 */
	private final AiModelMapper aiModelMapper;

	/**
	 * 生成AI海报
	 * <p>
	 * 根据模板样式和用户提示词生成海报 在海报内容前缀加模板CSS样式 支持二维码嵌入和自定义模型选择
	 * @param posterDTO 包含模板ID、提示词和二维码的请求参数
	 * @return 流式输出的海报内容
	 */
	@Override
	public Flux<AiMessageResultDTO> generatePoster(AiPosterDTO posterDTO) {
		AiPosterEntity posterEntity = baseMapper.selectById(Long.parseLong(posterDTO.getTemplateId()));
		Pair<StreamingChatModel, AiNoMemoryStreamAssistantService> pair = modelProvider
			.getAiNoMemoryStreamAssistant(posterDTO.getModelName());

		return pair.getValue()
			.chat(PromptBuilder.render("ai-poster.st",
					Map.of(AiPosterEntity.Fields.templateStyle, posterEntity.getTemplateStyle(),
							AiPosterDTO.Fields.qrCode, posterDTO.getQrCode(), AiPosterDTO.Fields.prompt,
							posterDTO.getPrompt())))
			.startWith(posterEntity.getTemplateCss())
			.map(AiMessageResultDTO::new);
	}

}
