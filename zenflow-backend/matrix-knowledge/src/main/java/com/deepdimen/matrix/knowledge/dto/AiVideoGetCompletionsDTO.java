package com.deepdimen.matrix.knowledge.dto;

import lombok.Data;

/**
 * AI视频补全数据传输对象
 * <p>
 * 用于AI视频生成完成后获取视频结果的数据传输对象
 *
 * @author caoyuan
 * @date 2025/05/07
 */
@Data
public class AiVideoGetCompletionsDTO {

	/**
	 * 请求ID
	 * <p>
	 * 用于标识视频生成请求的唯一标识符
	 */
	String requestId;

}
