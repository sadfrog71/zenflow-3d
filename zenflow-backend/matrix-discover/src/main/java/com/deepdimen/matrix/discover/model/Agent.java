package com.deepdimen.matrix.discover.model;

import lombok.Data;

@Data
public class Agent {

	private String author;

	private AgentConfig config;

	private String createdAt;

	private String homepage;

	private String identifier;

	private AgentMeta meta;

	private int schemaVersion;

}