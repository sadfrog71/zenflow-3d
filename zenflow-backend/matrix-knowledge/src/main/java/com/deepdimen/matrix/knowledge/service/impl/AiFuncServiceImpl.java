package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.knowledge.dto.AiConfirmFormDTO;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.service.AiFuncService;
import com.deepdimen.matrix.knowledge.support.function.FunctionCalling;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI函数服务实现类
 * <p>
 * 管理和查询AI功能函数列表 提供可用函数的显示信息 支持函数过滤和列表展示
 *
 * @author caoyuan
 * @date 2025/06/03
 */
@Service
@RequiredArgsConstructor
public class AiFuncServiceImpl implements AiFuncService {

	/**
	 * 函数调用处理器列表 注入所有实现FunctionCalling接口的Bean
	 */
	private final List<FunctionCalling> functionCallingList;

	/**
	 * 获取可展示的函数列表
	 * <p>
	 * 过滤出标记为可显示的函数 返回函数名称和展示信息
	 * @return 包含函数信息的列表
	 */
	@Override
	public R listFunctionCalling() {
		return R.ok(functionCallingList.stream()
			.filter(FunctionCalling::showFunction)
			.map(functionCalling -> Map.of("funcName", functionCalling.functionName(), "showInfo",
					functionCalling.showInfo()))
			.toList());
	}

	/**
	 * 提交函数
	 * @param confirmFormDTO 确认表单DTO
	 * @return 提交结果
	 */
	@Override
	public R submitFunction(AiConfirmFormDTO confirmFormDTO) {
		Optional<FunctionCalling> functionCallingOptional = functionCallingList.stream()
			.filter(functionCalling -> StrUtil.equals(MapUtil.getStr(confirmFormDTO, AiConfirmFormDTO.Fields.funcName),
					functionCalling.functionName()))
			.findFirst();

		if (functionCallingOptional.isEmpty()) {
			return R.failed("未找到对应的函数调用处理器");
		}

		ChatMessageDTO.ExtDetails extDetails = new ChatMessageDTO.ExtDetails();
		extDetails.setFuncName(confirmFormDTO.getFuncName());

		R<String> submittedR = functionCallingOptional.get()
			.submitForm(confirmFormDTO, SecurityUtils.getUser(), extDetails);

		// 返回结果 ,如果业务处理成功的话，增加跳转路由标识
		AiMessageResultDTO aiMessageResultDTO = new AiMessageResultDTO();
		if (!submittedR.isOk()) {
			aiMessageResultDTO.setMessage(submittedR.getMsg());
			return R.failed(aiMessageResultDTO);
		}

		aiMessageResultDTO.setMessage(submittedR.getData());
		aiMessageResultDTO.setFunctionName(functionCallingOptional.get().functionName());
		aiMessageResultDTO.setPath(functionCallingOptional.get().routePath());
		return R.ok(aiMessageResultDTO);
	}

}
