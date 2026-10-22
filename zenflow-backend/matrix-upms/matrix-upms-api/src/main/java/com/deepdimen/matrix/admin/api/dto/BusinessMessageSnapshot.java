package com.deepdimen.matrix.admin.api.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * Permission-filtered, read-only message fact exposed to other domains.
 */
@Data
public class BusinessMessageSnapshot {

	private Long id;

	private String title;

	private String content;

	private String sourceType;

	private String eventType;

	private String priority;

	private String targetPath;

	private Boolean actionRequired;

	private LocalDateTime occurredAt;

}
