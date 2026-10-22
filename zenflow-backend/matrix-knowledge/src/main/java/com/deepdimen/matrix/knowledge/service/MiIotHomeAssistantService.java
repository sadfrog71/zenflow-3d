package com.deepdimen.matrix.knowledge.service;

import com.deepdimen.matrix.knowledge.dto.MiIotServiceDTO;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 小米IoT家庭助理服务接口 提供与小米智能家居设备的集成能力，支持设备控制和状态查询
 *
 * @author caoyuan
 * @date 2024/12/22
 */
public interface MiIotHomeAssistantService {

	/**
	 * 调用IoT设备服务 向指定域的设备发送控制命令或查询状态
	 * @param domain 设备域，如light（灯光）、switch（开关）、climate（空调）等
	 * @param service 服务名称，如turn_on（打开）、turn_off（关闭）、toggle（切换）等
	 * @param request IoT请求对象，包含设备实体ID和服务参数
	 * @return 返回设备响应结果，包含执行状态和设备信息
	 */
	@PostExchange("/api/services/{domain}/{service}")
	String services(@PathVariable String domain, @PathVariable String service,
			@RequestBody MiIotServiceDTO.IotRequest request);

}
