package com.deepdimen.matrix.knowledge.support.dataquery;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.model.output.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.Identity;

class DataQueryResultAnalysisTest {

	DataQueryAiChatTest f;

	DataQueryResultAnalysis analysis;

	String query;

	AtomicInteger calls;

	AtomicReference<StreamingChatResponseHandler> handler;

	AtomicReference<ChatRequest> sent;

	CountDownLatch invoked;

	boolean immediate = true;

	@BeforeEach
	void setup() throws Exception {
		f = new DataQueryAiChatTest();
		f.setup();
		try (var c = f.source.getConnection()) {
			ScriptUtils.executeSqlScript(c, new FileSystemResource("../docs/dataquery/analysis-schema.sql"));
		}
		f.modelSql("SELECT company_id,COUNT(*) AS total FROM dev_device GROUP BY company_id ORDER BY company_id", 0);
		query = f.rule.process(f.message(9600, "比较这些公司的设备数量，当前仅返回结果"))
			.blockFirst()
			.getDataQuery()
			.get("queryId")
			.toString();
		calls = new AtomicInteger();
		handler = new AtomicReference<>();
		sent = new AtomicReference<>();
		invoked = new CountDownLatch(1);
		analysis = create();
	}

	DataQueryResultAnalysis create() {
		DataQueryResultAnalysis.Access access = (owner, id) -> {
			var v = f.metadata.visualizations().read(owner, id);
			if (v.snapshot() == null)
				throw new DataQueryException("ANALYSIS_SNAPSHOT_UNAVAILABLE");
			var q = f.metadata.findOwnedQuery(owner, id).orElseThrow();
			try {
				return new DataQueryResultAnalysis.Source(q.originalQuestion(), v.snapshot(),
						DataQueryMetadataRepository.fingerprint(f.json.writeValueAsString(v.snapshot())));
			}
			catch (Exception e) {
				throw new IllegalStateException(e);
			}
		};
		return new DataQueryResultAnalysis(f.source, f.json, f.clock, access,
				(owner, id, name) -> new DataQueryResultAnalysis.Model(name == null ? "original-model" : name,
						"fake-provider-model", () -> new StreamingChatModel() {
							@Override
							public void doChat(ChatRequest request, StreamingChatResponseHandler h) {
								calls.incrementAndGet();
								sent.set(request);
								handler.set(h);
								invoked.countDown();
								if (immediate) {
									h.onPartialResponse("返回结果中，");
									h.onPartialResponse("A公司的数量较多。原因尚需进一步核验。");
									h.onCompleteResponse(response(FinishReason.STOP));
								}
							}
						}));
	}

	ChatResponse response(FinishReason reason) {
		return ChatResponse.builder()
			.aiMessage(AiMessage.from("返回结果中，A公司的数量较多。原因尚需进一步核验。"))
			.finishReason(reason)
			.tokenUsage(new TokenUsage(320, 40, 360))
			.build();
	}

	DataQueryResultAnalysis.Request request(int revision) {
		return new DataQueryResultAnalysis.Request(UUID.randomUUID().toString(), revision, "explicit-choice",
				"请重点关注差异，也可提出待验证的假设");
	}

	List<DataQueryResultAnalysis.Event> execute(DataQueryResultAnalysis.Request request) {
		return analysis.start(f.owner, query, request).collectList().block(Duration.ofSeconds(10));
	}

	@AfterEach
	void cleanup() {
		analysis.close();
		f.cleanup();
	}

	@Test
	void streamsRealModelOutputSavesIndependentUsageAndNeverReexecutesSource() {
		int count = f.executions.get();
		var events = execute(request(0));
		assertThat(events.stream().map(DataQueryResultAnalysis.Event::type).toList()).containsExactly("started",
				"delta", "delta", "done");
		var view = analysis.read(f.owner, query);
		assertThat(view.result().status()).isEqualTo("SUCCEEDED");
		assertThat(view.result().content()).contains("原因尚需");
		assertThat(view.result().modelAlias()).isEqualTo("explicit-choice");
		assertThat(view.result().inputTokens()).isEqualTo(320);
		assertThat(view.result().totalTokens()).isEqualTo(360);
		assertThat(view.revision()).isEqualTo(1);
		assertThat(sent.get().toolSpecifications()).isEmpty();
		assertThat(sent.get().messages().toString()).contains("请重点关注差异", "不要求固定标题", "RETURNED_ROWS_ONLY");
		assertThat(f.executions.get()).isEqualTo(count);
		try (var reopened = create()) {
			assertThat(reopened.read(f.owner, query).result()).isEqualTo(view.result());
		}
		assertThat(calls.get()).isEqualTo(1);
	}

	@Test
	void retryIsIdempotentButReusingRequestWithOtherFocusIsRejected() {
		var req = request(0);
		execute(req);
		var again = execute(req);
		assertThat(again).hasSize(1);
		assertThat(again.get(0).type()).isEqualTo("done");
		assertThat(calls.get()).isEqualTo(1);
		var changed = new DataQueryResultAnalysis.Request(req.requestId(), 0, req.modelName(), "changed");
		assertThat(execute(changed).get(0).code()).isEqualTo("ANALYSIS_REQUEST_CHANGED");
	}

	@Test
	void staleRevisionDoesNotGenerateOrOverwriteSuccess() {
		execute(request(0));
		assertThat(execute(request(0)).get(0).code()).isEqualTo("ANALYSIS_CHANGED");
		assertThat(calls.get()).isEqualTo(1);
		assertThat(analysis.read(f.owner, query).result()).isNotNull();
	}

	@Test
	void crossTenantCrossUserRevokedAndChangedQuestionCannotReadOrAnalyze() {
		for (var user : List.of(new Identity("9", "2"), new Identity("1", "3"))) {
			assertThatThrownBy(() -> analysis.read(user, query)).isInstanceOf(DataQueryException.class);
			assertThat(analysis.start(user, query, request(0)).blockFirst(Duration.ofSeconds(5)).type())
				.isEqualTo("error");
		}
		f.jdbc.update("UPDATE ai_chat_record SET question_text='changed' WHERE record_id=9600");
		assertThatThrownBy(() -> analysis.read(f.owner, query)).hasMessage("QUERY_NOT_FOUND");
		assertThat(calls.get()).isZero();
	}

	@Test
	void revokedAuthorizationBlocksSavedResult() {
		execute(request(0));
		f.jdbc.update("UPDATE ai_dataquery_access SET access_revision='revoked',targets_json='[]'");
		assertThatThrownBy(() -> analysis.read(f.owner, query)).isInstanceOf(DataQueryException.class);
	}

	@Test
	void cancelledStreamCancelsProviderAndCannotBecomeSuccessOnLateCallback() throws Exception {
		immediate = false;
		var req = request(0);
		var subscription = analysis.start(f.owner, query, req).subscribe();
		assertThat(invoked.await(5, TimeUnit.SECONDS)).isTrue();
		var cancelled = new AtomicBoolean();
		handler.get()
			.onPartialResponse(new PartialResponse("未完成的分析"), new PartialResponseContext(new StreamingHandle() {
				public void cancel() {
					cancelled.set(true);
				}

				public boolean isCancelled() {
					return cancelled.get();
				}
			}));
		subscription.dispose();
		assertThat(cancelled.get()).isTrue();
		handler.get().onCompleteResponse(response(FinishReason.STOP));
		var view = analysis.read(f.owner, query);
		assertThat(view.attempt().status()).isEqualTo("CANCELLED");
		assertThat(view.result()).isNull();
	}

	@Test
	void simultaneousDuplicateDoesNotStartAnotherModel() throws Exception {
		immediate = false;
		var req = request(0);
		var subscription = analysis.start(f.owner, query, req).subscribe();
		assertThat(invoked.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(execute(req).get(0).code()).isEqualTo("ANALYSIS_IN_PROGRESS");
		assertThat(execute(request(1)).get(0).code()).isEqualTo("ANALYSIS_BUSY");
		assertThat(calls.get()).isEqualTo(1);
		subscription.dispose();
	}

	@Test
	void failedRegenerationKeepsPreviousSuccessAndDoesNotExposeProviderException() throws Exception {
		execute(request(0));
		immediate = false;
		var events = new CopyOnWriteArrayList<DataQueryResultAnalysis.Event>();
		var subscription = analysis.start(f.owner, query, request(1)).subscribe(events::add);
		for (int i = 0; i < 100 && calls.get() < 2; i++)
			Thread.sleep(20);
		assertThat(calls.get()).isEqualTo(2);
		handler.get().onError(new RuntimeException("api-key-and-private-uri"));
		var view = analysis.read(f.owner, query);
		assertThat(view.attempt().status()).isEqualTo("FAILED");
		assertThat(view.result().status()).isEqualTo("SUCCEEDED");
		assertThat(view.attempt().code()).isEqualTo("ANALYSIS_MODEL_FAILED");
		assertThat(events.toString()).doesNotContain("api-key-and-private-uri");
		subscription.dispose();
	}

	@Test
	void lengthCompletionIsNotReportedAsSuccessfulSavedAnalysis() throws Exception {
		immediate = false;
		var subscription = analysis.start(f.owner, query, request(0)).subscribe();
		assertThat(invoked.await(5, TimeUnit.SECONDS)).isTrue();
		handler.get().onPartialResponse("部分解释");
		handler.get().onCompleteResponse(response(FinishReason.LENGTH));
		var view = analysis.read(f.owner, query);
		assertThat(view.attempt().status()).isEqualTo("INCOMPLETE");
		assertThat(view.attempt().outputTokens()).isEqualTo(40);
		assertThat(view.result()).isNull();
		subscription.dispose();
	}

	@Test
	void revokedDuringGenerationErasesPartialAndDoesNotPersistSuccess() throws Exception {
		immediate = false;
		var events = new CopyOnWriteArrayList<DataQueryResultAnalysis.Event>();
		var sub = analysis.start(f.owner, query, request(0)).subscribe(events::add);
		assertThat(invoked.await(5, TimeUnit.SECONDS)).isTrue();
		handler.get().onPartialResponse("已生成部分");
		f.jdbc.update("UPDATE sys_user SET lock_flag='1' WHERE user_id=2");
		handler.get().onCompleteResponse(response(FinishReason.STOP));
		assertThat(events.get(events.size() - 1).type()).isEqualTo("error");
		assertThat(f.jdbc.queryForObject("SELECT result_json FROM ai_dataquery_analysis", String.class)).isNull();
		assertThat(f.jdbc.queryForObject("SELECT attempt_json FROM ai_dataquery_analysis", String.class))
			.doesNotContain("已生成部分");
		sub.dispose();
	}

	@Test
	void missingAnalysisTableFailsOnlyAnalysisBeforeCallingModel() {
		f.jdbc.execute("DROP TABLE ai_dataquery_analysis");
		assertThat(execute(request(0)).get(0).type()).isEqualTo("error");
		assertThat(calls.get()).isZero();
		assertThat(f.metadata.visualizations().read(f.owner, query).status()).isEqualTo("AVAILABLE");
	}

	@Test
	void modelUsageMissingIsUnknownNotFabricatedZero() throws Exception {
		immediate = false;
		var sub = analysis.start(f.owner, query, request(0)).subscribe();
		assertThat(invoked.await(5, TimeUnit.SECONDS)).isTrue();
		handler.get()
			.onCompleteResponse(ChatResponse.builder()
				.aiMessage(AiMessage.from("只说明当前返回结果。"))
				.finishReason(FinishReason.STOP)
				.build());
		assertThat(analysis.read(f.owner, query).result().totalTokens()).isNull();
		sub.dispose();
	}

	@Test
	void analysisModelCannotInheritOriginalChatWriteContext() {
		String originalAnswer = f.jdbc.queryForObject("SELECT answer_text FROM ai_chat_record WHERE record_id=9600",
				String.class);
		analysis.close();
		var standalone = new DataQueryResultAnalysis(f.source, f.json, f.clock, (owner, id) -> {
			var snapshot = f.metadata.visualizations().read(owner, id).snapshot();
			return new DataQueryResultAnalysis.Source("原问题", snapshot, "stable");
		}, (owner, id, alias) -> {
			var old = new com.deepdimen.matrix.knowledge.dto.ChatMessageDTO();
			old.setMessageKey(9600L);
			com.deepdimen.matrix.knowledge.support.util.ChatMessageContextHolder.set(old);
			return new DataQueryResultAnalysis.Model("choice", "model", () -> {
				assertThat(com.deepdimen.matrix.knowledge.support.util.ChatMessageContextHolder.get()).isNull();
				return new StreamingChatModel() {
					@Override
					public void doChat(ChatRequest request, StreamingChatResponseHandler h) {
						assertThat(com.deepdimen.matrix.knowledge.support.util.ChatMessageContextHolder.get()).isNull();
						h.onCompleteResponse(response(FinishReason.STOP));
					}
				};
			});
		});
		analysis = standalone;
		assertThat(execute(request(0)).get(1).view().result().status()).isEqualTo("SUCCEEDED");
		assertThat(f.jdbc.queryForObject("SELECT answer_text FROM ai_chat_record WHERE record_id=9600", String.class))
			.isEqualTo(originalAnswer);
	}

}
