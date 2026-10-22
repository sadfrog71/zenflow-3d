package com.deepdimen.matrix.knowledge.support.function;

import cn.hutool.core.util.StrUtil;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.dto.BaseAiRequest;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.dto.MiIotServiceDTO;
import com.deepdimen.matrix.knowledge.service.MiIotHomeAssistantService;
import com.deepdimen.matrix.knowledge.support.annotation.FieldPrompt;
import com.deepdimen.matrix.knowledge.support.constant.MiIotServiceEnums;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 米家设备控制函数调用实现类
 *
 * 该类实现了通过AI对话控制米家智能家居设备的功能 支持根据用户的自然语言描述来开关设备
 *
 * @author caoyuan
 * @date 2024/12/21
 */
@Component
@RequiredArgsConstructor
public class MiIotFunctionCalling implements FunctionCalling<MiIotRequest> {

	private final MiIotHomeAssistantService homeAssistantService;

	private final AiKnowledgeProperties aiKnowledgeProperties;

	/**
	 * 显示名称 在前端功能列表中显示的函数名称和描述
	 * @return {@link String } 函数的显示信息
	 */
	@Override
	public String showInfo() {
		return "智能家居，能根据您的描述帮您控制米家设备。比如：天黑了";
	}

	/**
	 * 检查参数是否正确 目前直接返回成功，实际的参数验证在handle方法中进行
	 * @param miIotRequest 米家设备控制请求参数
	 * @param userDetails 用户信息
	 * @param extDetails 扩展信息
	 * @return R<String> 参数检查结果
	 */
	@Override
	public R checkParams(MiIotRequest miIotRequest, MatrixUser userDetails, ChatMessageDTO.ExtDetails extDetails) {
		return R.ok();
	}

	/**
	 * 业务处理逻辑 1. 检查米家设备控制功能是否开启 2. 遍历配置的设备列表 3. 根据用户请求的开关状态调用对应的服务
	 * @param miIotRequest 米家设备控制请求
	 * @param userDetails 用户详细信息
	 * @param extDetails 扩展详细信息
	 * @return {@link R }<{@link String }> 设备控制结果
	 */
	@Override
	public R<String> handle(MiIotRequest miIotRequest, MatrixUser userDetails, ChatMessageDTO.ExtDetails extDetails) {
		if (!aiKnowledgeProperties.getMiIot().isEnabled()) {
			return R.failed(StrUtil.format("未链接米家设备控制，无法执行 {}", miIotRequest.getIsOpen()));
		}

		for (String entityId : aiKnowledgeProperties.getMiIot().getEntityIdList()) {
			MiIotServiceEnums serviceEnum = MiIotServiceEnums.getEnumByCode(miIotRequest.getIsOpen());
			homeAssistantService.services(serviceEnum.getDomain(), serviceEnum.getService(),
					MiIotServiceDTO.IotRequest.builder().entityId(entityId).build());
		}
		return R.ok("设备控制成功");
	}

}

/**
 * 米家设备控制请求参数类 用于接收AI解析后的设备控制指令
 */
@Data
@EqualsAndHashCode(callSuper = true)
class MiIotRequest extends BaseAiRequest {

	/**
	 * 设备开关状态 0: 关闭设备 1: 开启设备
	 */
	@FieldPrompt("是否开启，0关闭 ，1开启")
	private int isOpen;

}
