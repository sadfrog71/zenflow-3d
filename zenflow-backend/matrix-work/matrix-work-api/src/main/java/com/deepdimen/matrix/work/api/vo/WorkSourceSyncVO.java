package com.deepdimen.matrix.work.api.vo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WorkSourceSyncVO {

	private Long sourceId;

	private int scanned;

	private int inserted;

	private int duplicates;

	private LocalDateTime syncedAt;

}
