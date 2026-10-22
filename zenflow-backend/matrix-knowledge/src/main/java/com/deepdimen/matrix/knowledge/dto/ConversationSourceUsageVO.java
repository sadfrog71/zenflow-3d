package com.deepdimen.matrix.knowledge.dto;

import java.util.List;

import lombok.Data;

@Data
public class ConversationSourceUsageVO {

	private List<SourceUsageItem> items;

	@Data
	public static class SourceUsageItem {
		private String sourceType;
		private Long chatCount;
		private Long promptTokens;
		private Long completionTokens;
		private Long totalTokens;
		private Double percentage;
	}

}
