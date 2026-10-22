package com.deepdimen.matrix.knowledge.support.dataquery;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Recognizes standalone metadata requests. The template is retained for v1 history only.
 */
final class DataQueryDatasetOverview {

	private static final Pattern DATASET_DESCRIPTION = Pattern.compile(
            "(?:当前|这个|本)数据集(?:有|包含|包含了|包括)?哪些(?:表和字段|表|字段)[？?。]?|梳理当前数据集[，,]看看可以分析哪些问题[？?。]?|梳理下数据[，,]帮我看下可以问哪些问题[，,]不少于[1-9][0-9]?个[，,]每个问题都要具体[，,]我希望直接复制就可以去问答[？?。]?");

    private static final Pattern CAPABILITY = Pattern.compile(
			"(?:请|请问|麻烦)?(?:介绍一下|介绍下|说说|介绍)?(?:你|你们|这个数据集|当前数据集|本数据集|数据集|你这个数据集|你们这个数据集)(?:都)?(?:能|可以|支持)(?:查|查询|查到)(?:哪些数据|哪些类型的数据|什么类型的数据|什么数据|什么|哪些内容)(?:呢|吗|呀)?");

	private static final Pattern RECOMMENDATION = Pattern.compile(
			"(?:请|请问|麻烦)?(?:你|你们)?(?:能不能|能否|可以|可不可以)?(?:帮我|给我|为我)?(?:推荐|生成|列出|列举)(?:一下|下)?(?:一些|几个|[1-9][0-9]?个|[一二三四五六七八九十]+个)?(?:适合(?:这个|当前)?数据集的|可以(?:查询|问)的)?(?:业务|查询|问数)?问题(?:给我看看|我看看|看看|吗|吧)?");

	static boolean matches(String question) {
		if (question == null || question.length() > 100)
			return false;
		// Keep internal punctuation and every requested filter/action intact. A compound
		// question must go through the normal model, not silently lose its second task.
		String normalized = question.strip()
			.replaceAll("[\\s\\u3000]+", "")
			.replaceAll("[？?！!。]+$", "")
			.toLowerCase(Locale.ROOT);
		return DATASET_DESCRIPTION.matcher(normalized).matches() || CAPABILITY.matcher(normalized).matches() || RECOMMENDATION.matcher(normalized).matches();
	}

	static Optional<String> answer(String question, DataQueryMetadataRepository.DatasetInventory inventory) {
		if (!matches(question))
			return Optional.empty();
		try {
			var tables = new ObjectMapper().readTree(inventory.schema());
			if (!tables.isArray() || tables.size() != inventory.tables().size())
				throw new IllegalArgumentException();
			var seen = new HashSet<String>();
			var labels = new java.util.ArrayList<String>();
			for (var table : tables) {
				String name = table.path("table_name").asText();
				if (!inventory.tables().contains(name) || !seen.add(name))
					throw new IllegalArgumentException();
				String label = table.path("table_comment").asText("").strip();
				if (label.isBlank() || label.length() > 80)
					label = name;
				labels.add(plain(label));
			}
			var text = new StringBuilder("可以。我能围绕当前数据集选中的 **").append(tables.size())
				.append(" 张表**，帮助你了解字段含义、查询明细和做统计。\n\n**包含的数据**\n");
			for (String label : labels.stream().limit(10).toList())
				text.append("- ").append(label).append("\n");
			if (labels.size() > 10)
				text.append("- 另有 ").append(labels.size() - 10).append(" 张已选表，可继续问我详细介绍。\n");
			text.append("\n**你可以继续问**\n")
				.append("- “")
				.append(labels.get(0))
				.append("有多少条？”\n")
				.append("- “帮我推荐 5 个适合这个数据集的业务问题。”\n")
				.append("- “这些数据可以按哪些时间或组织字段统计？”\n\n")
				.append("具体能否关联、如何判断完成或异常，需要依据现有字段、备注和已确认的业务口径；不会仅凭表名推断。这里介绍的是数据结构，不代表实时记录或已核实的统计结果。");
			return Optional.of(text.toString());
		}
		catch (Exception invalid) {
			throw new DataQueryException("DATASET_METADATA_REQUIRED");
		}
	}

	private static String plain(String value) {
		return value.replaceAll("[\\p{Cntrl}\\s]+", " ")
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replaceAll("([\\\\`*_{}\\[\\]()#!|])", "\\\\$1");
	}

}
