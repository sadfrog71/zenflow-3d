package com.deepdimen.matrix.knowledge.support.function;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.flow.api.feign.RemoteFlowApiFlowService;
import com.deepdimen.matrix.flow.constant.NodeUserTypeEnum;
import com.deepdimen.matrix.flow.dto.ProcessInstanceParamDto;
import com.deepdimen.matrix.flow.vo.FormGroupVo;
import com.deepdimen.matrix.knowledge.dto.BaseAiRequest;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.support.annotation.FieldPrompt;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 流程函数调用实现类 该类实现了基于Flowable工作流引擎的流程启动功能 可以根据用户的自然语言描述自动创建并启动相应的工作流程
 *
 * @author caoyuan
 * @date 2024/3/29
 * <p>
 * 流程新增函数
 */
@Component
@RequiredArgsConstructor
public class FlowFunctionCalling implements FunctionCalling<FlowRequest> {

	private final RemoteFlowApiFlowService flowService;

	/**
	 * 路由路径 当用户点击函数卡片时，会跳转到此路径对应的页面
	 * @return {@link String } 跳转路径
	 */
	@Override
	public String routePath() {
		return "/flow/task/started";
	}

	/**
	 * 显示名称 在前端功能列表中显示的函数名称和描述
	 * @return {@link String } 函数的显示信息
	 */
	@Override
	public String showInfo() {
		return "请假助手，能根据您的描述帮您发起请求流程调用flowable。比如：明天帮我请一天假，好兄弟结婚";
	}

	/**
	 * 检查参数是否正确 目前直接返回成功，实际的参数验证在handle方法中进行
	 * @param request 流程请求参数
	 * @param userDetails 用户信息
	 * @param extDetails 扩展信息
	 * @return R<String> 参数检查结果
	 */
	@Override
	public R<String> checkParams(FlowRequest request, MatrixUser userDetails, ChatMessageDTO.ExtDetails extDetails) {
		return R.ok();
	}

	/**
	 * 实际的流程启动处理逻辑 1. 获取用户有权限启动的流程列表 2. 根据用户输入的流程名称匹配对应的流程ID 3. 构建流程启动参数 4.
	 * 调用Flowable引擎启动流程实例
	 * @param request 流程请求参数，包含流程名称、请假原因、天数等
	 * @param user 当前用户信息
	 * @param extDetails 扩展信息
	 * @return R<String> 流程启动结果
	 */
	public R<String> handle(FlowRequest request, MatrixUser user, ChatMessageDTO.ExtDetails extDetails) {

		R<List<FormGroupVo>> listR = flowService.listCurrentUserStartGroup();

		if (Objects.isNull(listR.getData())) {
			return R.failed("创建失败，权限不足");
		}

		String flowId = "";
		for (FormGroupVo formGroupVo : listR.getData()) {
			for (FormGroupVo.FlowVo item : formGroupVo.getItems()) {
				String name = item.getName();
				if (StrUtil.containsAnyIgnoreCase(request.getFlowName(), name)) {
					flowId = item.getFlowId();
				}
			}
		}

		if (StrUtil.isBlank(flowId)) {
			return R.failed("创建失败，流程不存在");
		}

		// 获取流程配置详情
		ProcessInstanceParamDto processInstanceParamDto = new ProcessInstanceParamDto();
		processInstanceParamDto.setFlowId(flowId);
		processInstanceParamDto.setStartUserId(String.valueOf(user.getId()));

		Map<String, Object> paramMap = new HashMap<>();
		// 设置发起人
		Dict rootUser = Dict.create()
			.set("id", processInstanceParamDto.getStartUserId())
			.set("name", user.getUsername())
			.set("type", NodeUserTypeEnum.USER.getKey());
		paramMap.put("root", CollUtil.newArrayList(rootUser));
		paramMap.put("days", request.getDays());
		paramMap.put("reason", request.getReason());
		paramMap.put("startDate", request.getStartDate());

		processInstanceParamDto.setParamMap(paramMap);

		flowService.startProcessInstance(processInstanceParamDto);
		return R.ok(StrUtil.format("流程发起成功，流程名称：{}", request.getFlowName()));
	}

}

/**
 * 流程请求参数类 用于接收AI解析后的流程启动相关参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonClassDescription("流程信息")
class FlowRequest extends BaseAiRequest {

	/**
	 * 流程名称 示例：请假流程
	 */
	@FieldPrompt("流程名称：请假流程")
	private String flowName;

	/**
	 * 请假原因 描述请假的具体原因
	 */
	@FieldPrompt("原因：请假原因")
	private String reason;

	/**
	 * 请假天数
	 */
	@FieldPrompt("天数：请假天数")
	private Integer days;

	/**
	 * 请假开始日期 格式：yyyy-MM-dd
	 */
	@FieldPrompt("开始日期：请假开始日期，格式为 yyyy-MM-dd")
	private String startDate;

}
