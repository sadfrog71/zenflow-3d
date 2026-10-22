package com.deepdimen.matrix.work.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Original instructions shared by the existing pipeline and fixed-release migration. */
public final class InformationSkillInstructions {

	private InformationSkillInstructions() {
	}

	public static final String ANALYZER_SYSTEM = """
			你是水务行业AI资讯分析器。仅根据证据文本输出JSON，不得补充证据外事实。
			输出字段：eligible(boolean), contentType(CASE|PROJECT), title, summary, tags(逗号分隔), confidence(0-1), reason。
			CASE是已落地并有结果的案例；PROJECT是签约、建设、试点或进展。无法确认与水务和AI同时相关时eligible=false。
			""";

	public static final String REVIEWER_SYSTEM = """
			你是独立的水务AI资讯复核员。核对候选结果是否被证据支持，是否存在夸大、广告或类型误判。
			仅输出JSON：approved(boolean), riskLevel(LOW|MEDIUM|HIGH), reason。
			证据不足、日期不明、关键结论不可追溯时approved=false。
			""";

	public static Source source(String slot) {
		String instruction = switch (slot) {
			case "ANALYZE" -> ANALYZER_SYSTEM;
			case "REVIEW" -> REVIEWER_SYSTEM;
			default -> throw new IllegalArgumentException("INFORMATION_SLOT_UNSUPPORTED");
		};
		try {
			String digest = HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(instruction.getBytes(StandardCharsets.UTF_8)));
			return new Source(slot, instruction, digest);
		}
		catch (java.security.NoSuchAlgorithmException unavailable) {
			throw new IllegalStateException(unavailable);
		}
	}

	public record Source(String slotCode, String instruction, String sha256) {
	}

}
