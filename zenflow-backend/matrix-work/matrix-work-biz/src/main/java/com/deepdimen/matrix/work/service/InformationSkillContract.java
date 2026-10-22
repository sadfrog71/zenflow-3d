package com.deepdimen.matrix.work.service;

import com.deepdimen.matrix.skill.api.SkillContract.ResolvedSkillSnapshot;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * Canonical first-version contracts; fixed releases may not weaken the stage boundary.
 */
public final class InformationSkillContract {

	private InformationSkillContract() {
	}

	public static String outputSchema(String slot) {
		return switch (slot) {
			case "ANALYZE" -> """
					{"type":"object","additionalProperties":false,"required":["eligible","reason"],"properties":{
					"eligible":{"type":"boolean"},"contentType":{"type":"string","enum":["CASE","PROJECT"]},
					"title":{"type":"string","maxLength":1000},"summary":{"type":"string","maxLength":1000},
					"tags":{"type":"string","maxLength":500},"confidence":{"type":"number","minimum":0,"maximum":1},
					"reason":{"type":"string","maxLength":2000}}}
					""";
			case "REVIEW" ->
				"""
						{"type":"object","additionalProperties":false,"required":["approved","riskLevel","reason"],"properties":{
						"approved":{"type":"boolean"},"riskLevel":{"type":"string","enum":["LOW","MEDIUM","HIGH"]},
						"reason":{"type":"string","maxLength":2000}}}
						""";
			default -> throw new IllegalArgumentException("INFORMATION_SLOT_UNSUPPORTED");
		};
	}

	public static String inputSchema(ObjectMapper json, String slot) {
		InformationSkillInstructions.source(slot);
		try {
			ObjectNode schema = (ObjectNode) json.readTree(
					"""
							{"type":"object","additionalProperties":false,"required":["title","url","evidence","reviewDate"],"properties":{
							"title":{"type":["string","null"]},"url":{"type":["string","null"]},
							"evidence":{"type":"string","maxLength":12000},"reviewDate":{"type":"string"}}}
							""");
			if ("REVIEW".equals(slot)) {
				((com.fasterxml.jackson.databind.node.ArrayNode) schema.get("required")).add("analysis");
				((ObjectNode) schema.get("properties")).set("analysis", json.readTree(outputSchema("ANALYZE")));
			}
			return schema.toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public static void validate(ObjectMapper json, String slot, ResolvedSkillSnapshot release) {
		try {
			var reader = json.reader()
				.with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
			if (!reader.readTree(release.inputSchemaJson()).equals(json.readTree(inputSchema(json, slot)))
					|| !reader.readTree(release.outputSchemaJson()).equals(json.readTree(outputSchema(slot))))
				throw new IllegalArgumentException("INFORMATION_CONTRACT_INCOMPATIBLE");
		}
		catch (java.io.IOException | NullPointerException failure) {
			throw new IllegalArgumentException("INFORMATION_CONTRACT_INCOMPATIBLE", failure);
		}
	}

}
