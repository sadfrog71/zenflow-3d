package com.deepdimen.matrix.knowledge.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.mapper.AiModelMapper;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.security.ModelEndpointPolicy;

class AiModelServiceImplAssistantModelTest {

	@Test
	void selectingAssistantModelReusesAnExistingChatModel() {
		AiModelMapper mapper = mock(AiModelMapper.class);
		AiModelServiceImpl service = serviceWith(mapper);
		when(mapper.selectById(9L)).thenReturn(model(9L, "Chat"));
		when(mapper.update(any(AiModelEntity.class), any(Wrapper.class))).thenReturn(1);
		when(mapper.updateById(any(AiModelEntity.class))).thenReturn(1);

		service.setAssistantModel(9L, true);

		ArgumentCaptor<AiModelEntity> selected = ArgumentCaptor.forClass(AiModelEntity.class);
		verify(mapper).updateById(selected.capture());
		assertEquals(9L, selected.getValue().getId());
		assertEquals("1", selected.getValue().getAssistantModel());
	}

	@Test
	void nonChatModelCannotBecomeAssistantModel() {
		AiModelMapper mapper = mock(AiModelMapper.class);
		AiModelServiceImpl service = serviceWith(mapper);
		when(mapper.selectById(9L)).thenReturn(model(9L, "Embedding"));

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> service.setAssistantModel(9L, true));

		assertEquals("统一助手只能使用聊天模型", error.getMessage());
	}

	@Test
	void ordinaryModelCreationCannotSetAssistantBinding() {
		AiModelMapper mapper = mock(AiModelMapper.class);
		AiModelServiceImpl service = serviceWith(mapper);
		AiModelEntity request = model(null, "Chat");
		request.setAssistantModel("1");
		when(mapper.insert(any(AiModelEntity.class))).thenReturn(1);

		service.saveModel(request);

		assertEquals("0", request.getAssistantModel());
	}

	@Test
	void ordinaryModelEditPreservesAssistantBinding() {
		AiModelMapper mapper = mock(AiModelMapper.class);
		AiModelServiceImpl service = serviceWith(mapper);
		AiModelEntity existing = model(9L, "Chat");
		existing.setAssistantModel("1");
		AiModelEntity request = model(9L, "Chat");
		request.setAssistantModel("0");
		when(mapper.selectById(9L)).thenReturn(existing);
		when(mapper.updateById(any(AiModelEntity.class))).thenReturn(1);

		service.updateModel(request);

		assertEquals("1", request.getAssistantModel());
	}

	private static AiModelServiceImpl serviceWith(AiModelMapper mapper) {
		AiModelServiceImpl service = new AiModelServiceImpl(mock(ModelProvider.class), mock(ModelEndpointPolicy.class));
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		return service;
	}

	private static AiModelEntity model(Long id, String modelType) {
		AiModelEntity model = new AiModelEntity();
		model.setId(id);
		model.setModelType(modelType);
		model.setName("model-" + id);
		return model;
	}

}
