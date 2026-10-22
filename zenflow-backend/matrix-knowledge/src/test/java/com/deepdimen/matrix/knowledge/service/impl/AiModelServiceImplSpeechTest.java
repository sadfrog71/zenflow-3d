package com.deepdimen.matrix.knowledge.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.security.ModelEndpointPolicy;

class AiModelServiceImplSpeechTest {

	@Test
	void asrConnectionTestRequiresRealAudioInsteadOfReturningAFalsePositive() {
		ModelProvider modelProvider = mock(ModelProvider.class);
		ModelEndpointPolicy endpointPolicy = mock(ModelEndpointPolicy.class);
		AiModelServiceImpl service = new AiModelServiceImpl(modelProvider, endpointPolicy);
		AiModelEntity model = new AiModelEntity();
		model.setModelType("Voice");
		model.setProvider("Aliyun");
		model.setModelName("qwen3-asr-flash");
		model.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
		model.setApiKey("api-key");
		model.setExtData("{\"voiceType\":\"1\"}");
		when(endpointPolicy.validate(model.getBaseUrl())).thenReturn(URI.create(model.getBaseUrl()));

		R<?> response = service.testModel(model);

		Map<?, ?> result = (Map<?, ?>) response.getData();
		assertFalse((Boolean) result.get("success"));
		assertEquals("测试失败: 语音识别模型需上传真实音频验证", result.get("message"));
		verifyZeroInteractions(modelProvider);
	}

}
