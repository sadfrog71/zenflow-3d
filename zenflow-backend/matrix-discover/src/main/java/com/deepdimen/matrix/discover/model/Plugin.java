package com.deepdimen.matrix.discover.model;

import lombok.Data;

@Data
public class Plugin {

	private String author;

	private String createdAt;

	private String homepage;

	private String identifier;

	private String manifest;

	private PluginMeta meta;

	private int schemaVersion;

}