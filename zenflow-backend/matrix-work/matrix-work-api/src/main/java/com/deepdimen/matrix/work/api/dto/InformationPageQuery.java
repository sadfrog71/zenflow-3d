package com.deepdimen.matrix.work.api.dto;

import lombok.Data;

@Data
public class InformationPageQuery {
	private long current = 1;
	private long size = 6;
	private String contentType = "CASE";
	private String keyword;
	private Boolean favorite;
	private Boolean unread;
}
