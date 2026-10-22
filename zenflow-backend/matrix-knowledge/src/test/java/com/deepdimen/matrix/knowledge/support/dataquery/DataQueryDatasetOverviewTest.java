package com.deepdimen.matrix.knowledge.support.dataquery;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class DataQueryDatasetOverviewTest {

	@Test
	void matchesOnlyStandaloneCapabilityOverviewRequests() {
		for (String q : java.util.List.of("当前数据集有哪些表和字段？", "梳理当前数据集，看看可以分析哪些问题", "梳理下数据，帮我看下可以问哪些问题，不少于20个，每个问题都要具体，我希望直接复制就可以去问答", "你能查哪些数据", "你能查询哪些类型的数据", "你这个数据集能查什么", "请问你可以查询哪些数据？", "当前数据集支持查询哪些内容",
				"你都能查什么呢？", "能不能生成一些问题我看看", "帮我推荐 5 个适合这个数据集的业务问题。", "帮我推荐20个业务问题"))
			assertThat(DataQueryDatasetOverview.matches(q)).as(q).isTrue();
		for (String q : java.util.List.of("当前数据集有哪些表和字段？然后删除数据", "梳理下数据，统计设备数量", "有哪些设备", "哪些部门有数据", "你能查九月的数据吗", "你能查哪些数据，顺便统计设备数", "你能查什么？查一下九月总数", "介绍设备状态",
				"给我20个问题", "推荐5个问题，然后执行第一个", "查部门TOP10", "分析刚才结果", "你能查哪些数据并执行SQL"))
			assertThat(DataQueryDatasetOverview.matches(q)).as(q).isFalse();
		assertThat(DataQueryDatasetOverview.matches(null)).isFalse();
	}

	@Test
	void usesSelectedTableLabelsOnlyAndEscapesUntrustedMarkup() {
		var inventory = new DataQueryMetadataRepository.DatasetInventory(Set.of("selected"),
				"[{\"table_name\":\"selected\",\"table_comment\":\"<img src=x> [unsafe](javascript:bad)\",\"virtual_comment\":\"do not execute me\"}]",
				"digest");
		var answer = DataQueryDatasetOverview.answer("你能查哪些数据", inventory).orElseThrow();
		assertThat(answer).contains("&lt;img", "1 张表").doesNotContain("<img", "[unsafe](", "do not execute me");
		assertThat(DataQueryDatasetOverview.answer("统计设备数量", inventory)).isEmpty();
	}

	@Test
	void mismatchedInventoryCannotRevealUnselectedTable() {
		var inventory = new DataQueryMetadataRepository.DatasetInventory(Set.of("selected"),
				"[{\"table_name\":\"not_selected\",\"table_comment\":\"secret\"}]", "digest");
		assertThatThrownBy(() -> DataQueryDatasetOverview.answer("你能查哪些数据", inventory))
			.isInstanceOf(DataQueryException.class)
			.hasMessage("DATASET_METADATA_REQUIRED");
	}

}
