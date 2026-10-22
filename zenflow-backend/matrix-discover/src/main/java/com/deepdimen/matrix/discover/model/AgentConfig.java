package com.deepdimen.matrix.discover.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentConfig {

	private String model;

	private String systemRole;

	private List<String> plugins;

	private String provider;

	private Long datasetId;

}