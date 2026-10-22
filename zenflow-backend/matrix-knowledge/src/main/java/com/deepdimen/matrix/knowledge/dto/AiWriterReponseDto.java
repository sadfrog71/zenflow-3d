package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AiWriterReponseDto implements Serializable {

	private String delta;

	private String error;

	public AiWriterReponseDto(String delta) {
		this.delta = delta;
	}

}
