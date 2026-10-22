package com.deepdimen.matrix.work.api.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class InformationDetailVO extends InformationCardVO {
	private String content;
}
