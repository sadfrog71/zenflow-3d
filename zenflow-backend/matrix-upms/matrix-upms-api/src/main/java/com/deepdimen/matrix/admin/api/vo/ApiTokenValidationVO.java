package com.deepdimen.matrix.admin.api.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API Token 内部校验结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiTokenValidationVO {

	private Long id;

	private String tenantId;

	private String createBy;

}
