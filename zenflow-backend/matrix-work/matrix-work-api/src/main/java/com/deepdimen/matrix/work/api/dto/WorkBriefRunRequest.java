package com.deepdimen.matrix.work.api.dto;

import lombok.Data;

@Data
public class WorkBriefRunRequest {

	/** 相同输入是否仍强制生成新版本。 */
	private Boolean force = false;
}
