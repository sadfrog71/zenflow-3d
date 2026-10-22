package com.deepdimen.matrix.knowledge.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.deepdimen.matrix.common.sentinel.handle.GlobalBizExceptionHandler;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.service.AiModelService;

class AiModelControllerVoiceConflictTest {

	private static final String CONFLICT_MESSAGE = "当前租户的该语音能力默认模型已被并发修改，请刷新后重试";

	@Test
	void returnsStableBadRequestEnvelopeForDefaultVoiceConflict() throws Exception {
		AiModelService service = mock(AiModelService.class);
		when(service.saveModel(any(AiModelEntity.class))).thenThrow(new IllegalArgumentException(CONFLICT_MESSAGE));
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AiModelController(service))
			.setControllerAdvice(new GlobalBizExceptionHandler())
			.build();

		mockMvc
			.perform(post("/aiModel").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"asr\",\"modelType\":\"Voice\",\"defaultModel\":\"1\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(1))
			.andExpect(jsonPath("$.msg").value(CONFLICT_MESSAGE));
	}

	@Test
	void returnsStableBadRequestEnvelopeForDefaultVoiceUpdateConflict() throws Exception {
		AiModelService service = mock(AiModelService.class);
		when(service.updateModel(any(AiModelEntity.class))).thenThrow(new IllegalArgumentException(CONFLICT_MESSAGE));
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AiModelController(service))
			.setControllerAdvice(new GlobalBizExceptionHandler())
			.build();

		mockMvc
			.perform(put("/aiModel").contentType(MediaType.APPLICATION_JSON)
				.content("{\"id\":2,\"name\":\"asr\",\"modelType\":\"Voice\",\"defaultModel\":\"1\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(1))
			.andExpect(jsonPath("$.msg").value(CONFLICT_MESSAGE));
	}

}
