package com.deepdimen.matrix.knowledge.support.dataquery;

import java.math.BigDecimal;
import java.util.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Direct row projection for the existing category + value chart; never aggregates or
 * sorts.
 */
public final class DataQueryChartProjection {

	private DataQueryChartProjection() {
	}

	public static Optional<String> fromResult(Map<String, Object> result, String type) {
		if (result == null || type == null || !"SUCCEEDED".equals(result.get("status"))
				|| !Set.of("1", "2", "3").contains(type) || !(result.get("columns") instanceof List<?> columns)
				|| columns.size() != 2 || !(result.get("rows") instanceof List<?> rows) || rows.isEmpty())
			return Optional.empty();
		var labels = new ArrayList<String>();
		var values = new ArrayList<Object>();
		boolean distinguishRows = false;
		for (Object item : rows) {
			if (!(item instanceof List<?> row) || row.size() != 2
					|| (row.get(0) != null && !(row.get(0) instanceof String)))
				return Optional.empty();
			String label = row.get(0) == null ? "空值（源数据）" : row.get(0).toString();
			distinguishRows |= row.get(0) == null || label.isBlank();
			labels.add(label.isBlank() ? "空白（源数据）" : label);
			if (row.get(1) == null) {
				values.add(null);
				continue;
			}
			BigDecimal value;
			try {
				value = new BigDecimal(row.get(1).toString());
			}
			catch (NumberFormatException notNumeric) {
				return Optional.empty(); // Other result shapes keep the original chart
											// model path.
			}
			// JDBC intentionally returns decimals as strings. Never round a BIGINT or
			// high precision decimal to draw it.
			if (value.abs().compareTo(new BigDecimal("9007199254740991")) > 0
					|| BigDecimal.valueOf(value.doubleValue()).compareTo(value) != 0)
				throw new DataQueryException("CHART_NUMBER_NOT_REPRESENTABLE");
			values.add(value);
		}
		distinguishRows |= new HashSet<>(labels).size() != labels.size();
		if (distinguishRows)
			for (int i = 0; i < labels.size(); i++)
				labels.set(i, labels.get(i) + "（结果第" + (i + 1) + "行）");
		Map<String, Object> option;
		if ("2".equals(type)) {
			var slices = new ArrayList<Map<String, Object>>();
			for (int i = 0; i < values.size(); i++) {
				if (values.get(i) == null || ((BigDecimal) values.get(i)).signum() < 0)
					throw new DataQueryException("CHART_DATA_NOT_SUITABLE");
				slices.add(Map.of("name", labels.get(i), "value", values.get(i)));
			}
			option = Map.of("tooltip", Map.of("trigger", "item"), "series", List.of(Map.of("type", "pie", "name",
					columns.get(1), "radius", "50%", "stillShowZeroSum", false, "data", slices)));
		}
		else {
			option = Map.of("xAxis", Map.of("type", "category", "data", labels), "yAxis", Map.of("type", "value"),
					"series",
					List.of(Map.of("type", "1".equals(type) ? "line" : "bar", "name", columns.get(1), "data", values)));
		}
		try {
			return Optional.of(new ObjectMapper().writeValueAsString(option));
		}
		catch (JsonProcessingException error) {
			throw new DataQueryException("CHART_UNAVAILABLE");
		}
	}

}
