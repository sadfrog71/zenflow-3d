package com.deepdimen.matrix.discover.model;

import lombok.Data;

import java.util.List;

@Data
public class PluginResponse {

	private int schemaVersion;

	private List<Plugin> plugins;

	private List<String> tags;

}