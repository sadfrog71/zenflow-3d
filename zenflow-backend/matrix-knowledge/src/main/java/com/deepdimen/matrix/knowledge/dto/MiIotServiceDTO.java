package com.deepdimen.matrix.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * 小米IoT服务数据传输对象 用于封装小米IoT服务相关的请求和响应数据
 *
 * @author caoyuan
 * @date 2024/12/22
 */
@Data
public class MiIotServiceDTO {

	/**
	 * IoT请求内部类 用于封装发送到小米IoT服务的请求数据
	 *
	 * @author caoyuan
	 * @date 2024/12/22
	 */
	@Data
	@Builder
	public static class IotRequest {

		/**
		 * 实体ID 小米IoT设备的唯一标识符
		 */
		@JsonProperty("entity_id")
		private String entityId;

	}

	/**
	 * IoT响应内部类 用于封装小米IoT服务返回的响应数据
	 *
	 * @author caoyuan
	 * @date 2024/12/22
	 */
	@Data
	public static class IotResponse {

	}

}
