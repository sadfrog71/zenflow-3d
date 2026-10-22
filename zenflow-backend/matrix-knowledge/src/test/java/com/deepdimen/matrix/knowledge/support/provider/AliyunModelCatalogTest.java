package com.deepdimen.matrix.knowledge.support.provider;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import cn.hutool.json.JSONUtil;

class AliyunModelCatalogTest {

	private AliyunModelCatalog.Response page(int page, int total, String... names) {
		var models = JSONUtil.createArray();
		for (String name : names)
			models.add(JSONUtil.createObj().set("model", name));
		return new AliyunModelCatalog.Response(200, JSONUtil.createObj()
			.set("success", true)
			.set("output", JSONUtil.createObj().set("page_no", page).set("total", total).set("models", models)));
	}

	@Test
	void supportsWorkspaceAndRegionalHostsWithoutChangingTheCredentialOrigin() {
		assertEquals("https://workspace.cn-beijing.maas.aliyuncs.com/api/v1/models",
				AliyunModelCatalog.endpoint("https://workspace.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"));
		assertEquals("https://dashscope-intl.aliyuncs.com/api/v1/models",
				AliyunModelCatalog.endpoint("https://dashscope-intl.aliyuncs.com/compatible-mode/v1/"));
		assertEquals("https://dashscope.aliyuncs.com/api/v1/models",
				AliyunModelCatalog.endpoint("https://dashscope.aliyuncs.com/compatible-mode/v1"));
		assertEquals("https://cn-hongkong.dashscope.aliyuncs.com/api/v1/models",
				AliyunModelCatalog.endpoint("https://cn-hongkong.dashscope.aliyuncs.com/compatible-mode/v1"));
		for (String address : List.of("https://user@dashscope.aliyuncs.com/compatible-mode/v1",
				"https://dashscope.aliyuncs.com/compatible-mode/v1#fragment",
				"https://dashscope.aliyuncs.com.other.test/compatible-mode/v1",
				"https://proxy.example/compatible-mode/v1", "http://dashscope.aliyuncs.com/compatible-mode/v1",
				"https://dashscope.aliyuncs.com:8443/compatible-mode/v1", "https://dashscope.aliyuncs.com/custom",
				"https://dashscope.aliyuncs.com/compatible-mode/v1?extra=1"))
			assertNull(AliyunModelCatalog.endpoint(address));
	}

	@Test
	void followsAllPagesAndDeduplicatesRatherThanReturningJustTheFirstPage() {
		AtomicInteger calls = new AtomicInteger();
		List<String> result = AliyunModelCatalog.readAll("https://catalog.example/api/v1/models", (url, timeout) -> {
			int number = calls.incrementAndGet();
			assertTrue(url.contains("page_no=" + number));
			assertTrue(url.contains("page_size=100"));
			assertTrue(timeout > 0 && timeout <= 8000);
			return number == 1 ? page(1, 4, "a", "b") : page(2, 4, "b", "c");
		});
		assertEquals(List.of("a", "b", "c"), result);
		assertEquals(2, calls.get());
	}

	@Test
	void handlesAnEmptyCatalog() {
		assertEquals(List.of(), AliyunModelCatalog.readAll("url", (url, timeout) -> page(1, 0)));
	}

	@Test
	void fallsBackOnlyForUnsupportedFirstPages() {
		for (int status : List.of(404, 405))
			assertThrows(AliyunModelCatalog.Unsupported.class, () -> AliyunModelCatalog.readAll("url",
					(url, timeout) -> new AliyunModelCatalog.Response(status, null)));
		for (int status : List.of(401, 403, 429, 500, 302))
			assertThrows(IllegalStateException.class, () -> AliyunModelCatalog.readAll("url",
					(url, timeout) -> new AliyunModelCatalog.Response(status, null)));
	}

	@Test
	void rejectsPrematureChangingRepeatedOrOversizedPages() {
		for (int scenario = 0; scenario < 5; scenario++) {
			int mode = scenario;
			AtomicInteger calls = new AtomicInteger();
			assertThrows(IllegalStateException.class, () -> AliyunModelCatalog.readAll("url", (url, timeout) -> {
				int p = calls.incrementAndGet();
				if (mode == 0)
					return page(1, 10001, "a");
				if (p == 1)
					return page(1, 2, "a");
				return switch (mode) {
					case 1 -> page(2, 2);
					case 2 -> page(1, 2, "b");
					case 3 -> page(2, 3, "b");
					default -> new AliyunModelCatalog.Response(404, null);
				};
			}));
		}
	}

	@Test
	void boundsTheWholeRequestAndRedactsResponseLogging() {
		AtomicLong clock = new AtomicLong();
		assertThrows(IllegalStateException.class, () -> AliyunModelCatalog.readAll("url", (url, timeout) -> {
			clock.set(8_000_000_001L);
			return page(1, 2, "a");
		}, clock::get));
		assertEquals("CatalogResponse[REDACTED]",
				new AliyunModelCatalog.Response(400, JSONUtil.createObj().set("detail", "private")).toString());
	}

	@Test
	void rejectsAnOverBudgetFinalPageInsteadOfReportingSuccess() {
		AtomicLong clock = new AtomicLong();
		assertThrows(IllegalStateException.class, () -> AliyunModelCatalog.readAll("url", (url, timeout) -> {
			clock.set(8_000_000_001L);
			return page(1, 1, "a");
		}, clock::get));
	}

	@Test
	void rejectsInvalidRecordsRatherThanClaimingAnEmptySuccess() {
		assertThrows(IllegalStateException.class,
				() -> AliyunModelCatalog.readAll("url", (url, timeout) -> new AliyunModelCatalog.Response(200, null)));
		assertThrows(IllegalStateException.class,
				() -> AliyunModelCatalog.readAll("url", (url, timeout) -> page(1, 1, "")));
	}

}
