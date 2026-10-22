package com.deepdimen.matrix.admin.api.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 嵌入授权码上下文
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbedCodeContext implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String tenantId;

	private String username;

	private Long agentId;

	private Long apiTokenId;

	private LocalDateTime createdAt;

}
