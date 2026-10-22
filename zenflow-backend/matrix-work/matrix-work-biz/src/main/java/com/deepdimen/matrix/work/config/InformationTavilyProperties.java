package com.deepdimen.matrix.work.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "matrix.work.information.tavily")
public class InformationTavilyProperties {
	private boolean enabled = false;
	private String apiKey;
	private String baseUrl = "https://api.tavily.com";
	private String searchDepth = "basic";
	private int maxResults = 20;
	private int timeoutSeconds = 30;
}
