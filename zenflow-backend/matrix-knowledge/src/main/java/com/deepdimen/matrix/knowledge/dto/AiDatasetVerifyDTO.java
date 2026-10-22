package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

/**
 * AI知识库验证数据传输对象
 * <p>
 * 用于知识库访问权限验证，通过token验证用户是否有权访问指定知识库
 *
 * @author caoyuan
 * @date 2025/2/28
 */
@Data
public class AiDatasetVerifyDTO {

	/**
	 * 知识库ID 需要验证访问权限的知识库唯一标识
	 */
	private String datasetId;

	/**
	 * 访问令牌 用于验证用户身份和访问权限的安全令牌
	 */
	private String token;

}
