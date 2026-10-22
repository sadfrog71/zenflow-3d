package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class Html2DocxReqDto implements Serializable {

	private String html;

	private String baseUrl;

}
