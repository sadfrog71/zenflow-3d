package com.deepdimen.matrix.knowledge.support.dataquery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lossless prompt projection only. The inventory, authorization and digest stay
 * unchanged.
 */
final class DataQueryPromptSchema {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final List<String> TABLE = List.of("table_name", "table_comment", "virtual_comment", "fields");

	private static final List<String> FIELD = List.of("field_name", "field_type", "field_comment", "virtual_comment");

	private DataQueryPromptSchema() {
	}

	static String render(DataQueryMetadataRepository.DatasetInventory inventory) {
		String original = inventory.schema();
		try {
			JsonNode tables = JSON.readTree(original);
			if (!tables.isArray() || tables.size() != inventory.tables().size())
				return original;
			var output = JSON.createObjectNode();
			output.set("table_columns", JSON.valueToTree(TABLE));
			output.set("field_columns", JSON.valueToTree(FIELD));
			var compact = output.putArray("tables");
			var seen = new HashSet<String>();
			for (var table : tables) {
				if (!exactKeys(table, TABLE) || !table.path("table_name").isTextual()
						|| !inventory.tables().contains(table.path("table_name").asText())
						|| !seen.add(table.path("table_name").asText()) || !table.path("fields").isArray())
					return original;
				var row = compact.addArray();
				for (String key : TABLE.subList(0, 3))
					row.add(table.get(key));
				var fields = row.addArray();
				for (var field : table.path("fields")) {
					if (!exactKeys(field, FIELD))
						return original;
					var fieldRow = fields.addArray();
					for (String key : FIELD)
						fieldRow.add(field.get(key));
				}
			}
			String result = JSON.writeValueAsString(output);
			return result.length() < original.length() ? result : original;
		}
		catch (Exception incompatible) {
			// Forward-compatible: never discard a newly added metadata property or
			// subset.
			return original;
		}
	}

	private static boolean exactKeys(JsonNode node, List<String> names) {
		if (!node.isObject() || node.size() != names.size())
			return false;
		var keys = new HashSet<String>();
		node.fieldNames().forEachRemaining(keys::add);
		return keys.equals(Set.copyOf(names));
	}

}
