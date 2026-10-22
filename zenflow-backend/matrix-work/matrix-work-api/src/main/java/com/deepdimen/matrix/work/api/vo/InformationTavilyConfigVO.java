package com.deepdimen.matrix.work.api.vo;

import lombok.Data;

@Data
public class InformationTavilyConfigVO {

	private Long id;
	private Integer enabled;
	private Boolean configured;
	private String apiKeyMasked;
	private String baseUrl;
	private String searchDepth;
	private Integer maxResults;
	private Integer timeoutSeconds;
	private String source;
}
