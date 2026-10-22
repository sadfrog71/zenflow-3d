package com.deepdimen.matrix.discover.model;

import lombok.Data;

import java.util.List;

@Data
public class AgentResponse {

	private int schemaVersion;

	private List<Agent> agents;

	private List<String> tags;

}