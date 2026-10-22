package com.deepdimen.matrix.work.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class InformationPublishRequest {
	private Long itemId;
	private List<Long> orgIds;
}
