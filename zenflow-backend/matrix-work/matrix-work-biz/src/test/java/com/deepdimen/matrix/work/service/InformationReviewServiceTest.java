package com.deepdimen.matrix.work.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

class InformationReviewServiceTest {

	@Test
	void quarantinesEvidenceWhenTheOptionalRemoteModelPortIsNotConfigured() {
		@SuppressWarnings("unchecked")
		ObjectProvider<InformationReviewModel> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(null);
		InformationReviewService service = new InformationReviewService(provider, new ObjectMapper());

		InformationReviewService.ReviewResult result = service.review(null, "标题", "https://example.test", "证据");

		assertFalse(result.approved());
		assertEquals("未找到AI模型服务", result.reason());
	}

	@Test
	void suppliesTheBeijingReviewDateToBothModelStages() {
		@SuppressWarnings("unchecked")
		ObjectProvider<InformationReviewModel> provider = mock(ObjectProvider.class);
		InformationReviewModel model = mock(InformationReviewModel.class);
		when(provider.getIfAvailable()).thenReturn(model);
		when(model.chat(anyString(), anyString(), anyString())).thenReturn(
				"{\"eligible\":true,\"contentType\":\"CASE\",\"title\":\"案例\",\"summary\":\"摘要\",\"tags\":\"水务,AI\",\"confidence\":0.9,\"reason\":\"相关\"}",
				"{\"approved\":true,\"riskLevel\":\"LOW\",\"reason\":\"日期有效\"}");
		InformationReviewService service = new InformationReviewService(provider, new ObjectMapper());

		InformationReviewService.ReviewResult result = service.review(null, "标题", "https://example.test", "证据日期为2026-08-26");

		assertTrue(result.approved());
		ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
		verify(model, times(2)).chat(anyString(), anyString(), promptCaptor.capture());
		String currentDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
		for (String prompt : promptCaptor.getAllValues()) {
			assertTrue(prompt.contains("当前日期（北京时间）：" + currentDate));
			assertTrue(prompt.contains("早于或等于当前日期的日期不是未来日期"));
		}
	}

}
