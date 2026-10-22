package com.deepdimen.matrix.knowledge.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.mapper.AiModelMapper;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.security.ModelEndpointPolicy;

class AiModelServiceImplVoiceDefaultTest {

	@Test
	void changingTheDefaultAsrDoesNotUnsetTheDefaultTts() {
		AiModelMapper mapper = mock(AiModelMapper.class);
		AiModelServiceImpl service = new AiModelServiceImpl(mock(ModelProvider.class), mock(ModelEndpointPolicy.class));
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		AiModelEntity oldAsr = voiceModel(1L, "1", "1");
		AiModelEntity defaultTts = voiceModel(2L, "1", "0");
		AiModelEntity newAsr = voiceModel(3L, "1", "1");
		when(mapper.selectById(3L)).thenReturn(voiceModel(3L, "0", "1"));
		when(mapper.selectList(any())).thenReturn(List.of(oldAsr, defaultTts));
		when(mapper.updateById(any(AiModelEntity.class))).thenReturn(1);

		service.updateModel(newAsr);

		verify(mapper).updateById(oldAsr);
		verify(mapper, never()).updateById(defaultTts);
	}

	@Test
	void mapsConcurrentDefaultVoiceInsertToAStableClientError() {
		AiModelMapper mapper = mock(AiModelMapper.class);
		AiModelServiceImpl service = serviceWith(mapper);
		AiModelEntity newAsr = voiceModel(3L, "1", "1");
		DuplicateKeyException duplicate = voiceDefaultDuplicate();
		when(mapper.selectList(any())).thenReturn(List.of());
		when(mapper.insert(newAsr)).thenThrow(duplicate);

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> service.saveModel(newAsr));

		assertEquals("当前租户的该语音能力默认模型已被并发修改，请刷新后重试", error.getMessage());
		assertSame(duplicate, error.getCause());
	}

	@Test
	void mapsConcurrentDefaultVoiceUpdateToAStableClientError() {
		AiModelMapper mapper = mock(AiModelMapper.class);
		AiModelServiceImpl service = serviceWith(mapper);
		AiModelEntity newAsr = voiceModel(3L, "1", "1");
		DuplicateKeyException duplicate = voiceDefaultDuplicate();
		when(mapper.selectById(3L)).thenReturn(voiceModel(3L, "0", "1"));
		when(mapper.selectList(any())).thenReturn(List.of());
		when(mapper.updateById(newAsr)).thenThrow(duplicate);

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> service.updateModel(newAsr));

		assertEquals("当前租户的该语音能力默认模型已被并发修改，请刷新后重试", error.getMessage());
		assertSame(duplicate, error.getCause());
	}

	@Test
	void preservesUnrelatedDuplicateKeyErrors() {
		AiModelMapper mapper = mock(AiModelMapper.class);
		AiModelServiceImpl service = serviceWith(mapper);
		AiModelEntity newAsr = voiceModel(3L, "1", "1");
		DuplicateKeyException duplicate = new DuplicateKeyException(
				"Duplicate entry 'uk_ai_model_default_voice_capability' for key 'uk_ai_model_name'");
		when(mapper.selectList(any())).thenReturn(List.of());
		when(mapper.insert(newAsr)).thenThrow(duplicate);

		assertSame(duplicate, assertThrows(DuplicateKeyException.class,
				() -> service.saveModel(newAsr)));
	}

	@Test
	void mapsQualifiedVoiceDefaultKeyFromNestedDatabaseCause() {
		AiModelMapper mapper = mock(AiModelMapper.class);
		AiModelServiceImpl service = serviceWith(mapper);
		AiModelEntity newAsr = voiceModel(3L, "1", "1");
		DuplicateKeyException duplicate = new DuplicateKeyException("failed to execute update",
				new IllegalStateException(
						"Duplicate entry '1:1' for key 'ai_model.uk_ai_model_default_voice_capability'   "));
		when(mapper.selectList(any())).thenReturn(List.of());
		when(mapper.insert(newAsr)).thenThrow(duplicate);

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> service.saveModel(newAsr));

		assertEquals("当前租户的该语音能力默认模型已被并发修改，请刷新后重试", error.getMessage());
		assertSame(duplicate, error.getCause());
	}

	private static AiModelServiceImpl serviceWith(AiModelMapper mapper) {
		AiModelServiceImpl service = new AiModelServiceImpl(mock(ModelProvider.class), mock(ModelEndpointPolicy.class));
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		return service;
	}

	private static DuplicateKeyException voiceDefaultDuplicate() {
		return new DuplicateKeyException(
				"Duplicate entry '1:1' for key 'uk_ai_model_default_voice_capability'");
	}

	private static AiModelEntity voiceModel(Long id, String defaultModel, String voiceType) {
		AiModelEntity model = new AiModelEntity();
		model.setId(id);
		model.setName("voice-" + id);
		model.setModelType("Voice");
		model.setDefaultModel(defaultModel);
		model.setExtData("{\"voiceType\":\"" + voiceType + "\"}");
		return model;
	}

}
