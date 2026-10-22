package com.deepdimen.matrix.assistant.provider.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AssistantInternalRequestHasherTest {

	@Test
	void canonicalHashIsStableAcrossMapperAndMapOrdering() {
		ObjectMapper defaultMapper = new ObjectMapper();
		ObjectMapper sortedMapper = new ObjectMapper();
		sortedMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

		Map<String, String> firstMap = new LinkedHashMap<>();
		firstMap.put("z", "last");
		firstMap.put("a", "first");
		Map<String, String> secondMap = new LinkedHashMap<>();
		secondMap.put("a", "first");
		secondMap.put("z", "last");

		String first = new AssistantInternalRequestHasher(defaultMapper)
			.hash(new Payload("value", null, firstMap, List.of("one", "two")));
		String second = new AssistantInternalRequestHasher(sortedMapper)
			.hash(new Payload("value", null, secondMap, List.of("one", "two")));
		String different = new AssistantInternalRequestHasher(defaultMapper)
			.hash(new Payload("value", null, firstMap, List.of("two", "one")));

		assertEquals(first, second);
		assertNotEquals(first, different);
	}

	@Test
	void hashesTheExactSerializedBodyWithoutReparsingIt() {
		AssistantInternalRequestHasher hasher = new AssistantInternalRequestHasher(new ObjectMapper());

		assertEquals(hasher.hashSerializedBody("{\"a\":1,\"b\":2}"), hasher.hashSerializedBody("{\"a\":1,\"b\":2}"));
		assertNotEquals(hasher.hashSerializedBody("{\"a\":1,\"b\":2}"), hasher.hashSerializedBody("{\"b\":2,\"a\":1}"));
	}

	private record Payload(String zField, String aField, Map<String, String> values, List<String> orderedItems) {
	}

}
