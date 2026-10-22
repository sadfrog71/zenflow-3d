package com.deepdimen.matrix.knowledge.support.dataquery;

import cn.hutool.core.lang.Pair;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DataQueryAnalysisConfigurationTest {

	DataQueryAiChatTest f;

	DataQueryResultAnalysis service;

	String id;

	ModelProvider provider;

	AtomicReference<AiModelEntity> selected;

	@BeforeEach
	void setup() throws Exception {
		f = new DataQueryAiChatTest();
		f.setup();
		try (var c = f.source.getConnection()) {
			ScriptUtils.executeSqlScript(c, new FileSystemResource("../docs/dataquery/analysis-schema.sql"));
		}
		f.jdbc.execute("ALTER TABLE ai_chat_record ADD COLUMN model_name VARCHAR(128)");
		f.jdbc.execute(
				"CREATE TABLE ai_model(id BIGINT,tenant_id BIGINT,name VARCHAR(128),model_name VARCHAR(128),model_type VARCHAR(32),del_flag CHAR(1),response_limit INT,temperature DOUBLE)");
		f.jdbc.update(
				"INSERT INTO ai_model VALUES(70,1,'original','original-real','Chat','0',2048,0.4),(71,1,'chosen','chosen-real','Chat','0',2048,0.4),(72,9,'other-tenant','forbidden','Chat','0',2048,0.4),(73,1,'deleted','deleted-real','Chat','1',2048,0.4)");
		f.modelSql("SELECT COUNT(*) AS count FROM dev_device", 0);
		id = f.rule.process(f.message(9633, "设备数量")).blockFirst().getDataQuery().get("queryId").toString();
		f.jdbc.update("UPDATE ai_chat_record SET model_name='original'");
		provider = mock(ModelProvider.class);
		selected = new AtomicReference<>();
		when(provider.buildResolvedAiNoMemoryStreamAssistant(any(AiModelEntity.class))).thenAnswer(call -> {
			selected.set(call.getArgument(0));
			return Pair.of(new StreamingChatModel() {
				@Override
				public void doChat(ChatRequest request, StreamingChatResponseHandler h) {
					h.onCompleteResponse(ChatResponse.builder()
						.aiMessage(AiMessage.from("本次返回了设备数量。"))
						.finishReason(FinishReason.STOP)
						.build());
				}
			}, null);
		});
		service = new DataQueryAnalysisConfiguration().dataQueryResultAnalysis(f.source, f.metadata, f.json, provider);
	}

	@AfterEach
	void close() {
		service.close();
		f.cleanup();
	}

	DataQueryResultAnalysis.Request req(String name) {
		return new DataQueryResultAnalysis.Request(UUID.randomUUID().toString(), 0, name, "");
	}

	@Test
	void resolvesOnlyExplicitTenantModelAndKeepsItsSettings() {
		var events = service.start(f.owner, id, req("chosen")).collectList().block(Duration.ofSeconds(5));
		assertThat(events.get(events.size() - 1).view().result().modelName()).isEqualTo("chosen-real");
		assertThat(selected.get().getId()).isEqualTo(71L);
		assertThat(selected.get().getResponseLimit()).isEqualTo(2048);
		assertThat(selected.get().getTemperature()).isEqualTo(0.4);
		verify(provider, never()).getAiNoMemoryStreamAssistant(anyString());
	}

	@Test
	void absentSelectionUsesOriginalModelNotDefault() {
		service.start(f.owner, id, req(null)).blockLast(Duration.ofSeconds(5));
		assertThat(selected.get().getId()).isEqualTo(70L);
	}

	@Test
	void deletedAndOtherTenantModelsNeverFallBack() {
		for (String name : List.of("other-tenant", "deleted", "missing")) {
			var e = service.start(f.owner, id, req(name)).blockFirst(Duration.ofSeconds(5));
			assertThat(e.code()).isEqualTo("ANALYSIS_MODEL_UNAVAILABLE");
		}
		verifyZeroInteractions(provider);
	}

	@Test
	void httpUsesTrustedIdentityNoStoreAndRealSseFrames() throws Exception {
		assertThat(DataQueryAnalysisController.class
			.getMethod("generate", String.class, DataQueryResultAnalysis.Request.class)
			.isAnnotationPresent(com.deepdimen.matrix.common.api.encrypt.annotation.NoEncrypt.class)).isTrue();
		var mvc = MockMvcBuilders.standaloneSetup(new DataQueryAnalysisController(service)).build();
		String path = "/data-query/queries/" + id + "/analysis";
		mvc.perform(get(path)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
		var result = mvc
			.perform(post(path).contentType("application/json")
				.accept("text/event-stream")
				.content(f.json.writeValueAsString(req("chosen"))))
			.andExpect(request().asyncStarted())
			.andReturn();
		mvc.perform(asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(header().string("X-Accel-Buffering", "no"))
			.andExpect(content().contentTypeCompatibleWith("text/event-stream"));
		assertThat(result.getResponse().getContentAsString()).contains("data:", "SUCCEEDED");
		SecurityContextHolder.clearContext();
		mvc.perform(get(path)).andExpect(status().isUnauthorized());
	}

}
