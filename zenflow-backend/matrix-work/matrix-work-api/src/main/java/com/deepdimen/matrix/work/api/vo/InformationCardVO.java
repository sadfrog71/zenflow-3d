package com.deepdimen.matrix.work.api.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InformationCardVO {
	private Long id;
	private String contentType;
	private String title;
	private String sourceName;
	private String sourceUrl;
	private String summary;
	private String tags;
	private LocalDateTime publishedAt;
	private LocalDateTime deliveredAt;
	private boolean read;
	private boolean favorite;
}
