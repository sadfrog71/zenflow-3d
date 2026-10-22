package com.deepdimen.matrix.work.api.dto;

import lombok.Data;

@Data
public class InformationTavilyConfigRequest {

	private Boolean enabled;
	private String apiKey;
	private Boolean clearApiKey;
	private String baseUrl;
	private String searchDepth;
	private Integer maxResults;
	private Integer timeoutSeconds;
}
