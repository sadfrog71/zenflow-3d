package com.deepdimen.matrix.knowledge.support.rule;

import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.service.AiDatasetService;
import com.deepdimen.matrix.knowledge.support.handler.rag.strategy.UnifiedRagStrategy;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

class VectorChatRuleTest {

	@Test
	void shouldReturnReadableMessageWhenDatasetIsNotAccessible() {
		AiDatasetService datasetService = mock(AiDatasetService.class);
		ModelProvider modelProvider = mock(ModelProvider.class);
		UnifiedRagStrategy standardStrategy = mock(UnifiedRagStrategy.class);
		UnifiedRagStrategy vectorStrategy = mock(UnifiedRagStrategy.class);
		VectorChatRule rule = new VectorChatRule(datasetService, modelProvider, standardStrategy, vectorStrategy);
		ChatMessageDTO message = new ChatMessageDTO();
		message.setDatasetId(42L);
		message.setContent("测试问题");
		when(datasetService.getById(42L)).thenReturn(null);

		List<AiMessageResultDTO> results = rule.process(message).collectList().block();

		assertThat(results).extracting(AiMessageResultDTO::getMessage)
			.containsExactly("知识库不存在或当前用户无权访问");
		verifyZeroInteractions(modelProvider, standardStrategy, vectorStrategy);
	}

}
