package com.deepdimen.matrix.knowledge.support.function;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.deepdimen.matrix.admin.api.dto.UserDTO;
import com.deepdimen.matrix.admin.api.feign.RemoteUserService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.knowledge.dto.AiConfirmFormDTO;
import com.deepdimen.matrix.knowledge.dto.BaseAiRequest;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.support.annotation.FieldPrompt;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.FieldNameConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;

/**
 * 系统用户函数调用实现类
 *
 * 该类实现了通过AI对话创建系统用户的功能 可以根据用户的自然语言描述自动提取用户名、密码、手机号等信息 并创建新的系统用户
 *
 * @author caoyuan
 * @date 2024/3/29
 * <p>
 * 用户新增函数
 */
@Component
@RequiredArgsConstructor
public class SysUserFunctionCalling implements FunctionCalling<SysUserRequest> {

	private final RemoteUserService remoteUserService;

	@Value("classpath:forms/SysUserFunctionCallingForm.json")
	private Resource formResource;

	/**
	 * 路由路径 点击函数卡片后会跳转到用户管理页面
	 * @return {@link String } 跳转路径
	 */
	@Override
	public String routePath() {
		return "/admin/system/user/index";
	}

	/**
	 * 显示名称 在前端功能列表中显示的函数名称和描述
	 * @return {@link String } 函数的显示信息
	 */
	@Override
	public String showInfo() {
		return "系统助手，能根据您的描述帮您创建用户。比如：帮我创建一个系统用户， 用户名 tom 密码123456 手机号 12312312312";
	}

	/**
	 * 检查参数是否正确 1. 验证用户是否有创建用户的权限 2. 验证用户名格式（不允许中文） 3. 验证手机号格式 4. 验证密码是否为空
	 * @param request 用户创建请求参数
	 * @param userDetails 用户信息
	 * @param extDetails 扩展信息
	 * @return R<String> 参数检查结果
	 */
	@Override
	public R<String> checkParams(SysUserRequest request, MatrixUser userDetails, ChatMessageDTO.ExtDetails extDetails) {
		if (userDetails.getAuthorities().stream().noneMatch(e -> e.getAuthority().equals("sys_user_add"))) {
			return R.failed("创建失败，权限不足");
		}

		if (Validator.hasChinese(request.getUsername())) {
			return R.failed("创建失败，用户名格式为小写英文");
		}

		if (!Validator.isMobile(request.getPhone())) {
			return R.failed("创建失败，用户手机号格式不正确");
		}

		if (StrUtil.isBlank(request.getPassword())) {
			return R.failed("创建失败，用户密码格式不正确");
		}

		return R.ok();
	}

	/**
	 * 实际的用户创建处理逻辑 1. 构建用户DTO对象 2. 设置用户名、手机号、密码、租户ID 3. 调用远程用户服务创建用户 4. 返回创建结果
	 * @param request 用户创建请求参数
	 * @param userDetails 当前用户信息
	 * @param extDetails 扩展信息
	 * @return R<String> 用户创建结果
	 */
	public R<String> handle(SysUserRequest request, MatrixUser userDetails, ChatMessageDTO.ExtDetails extDetails) {
		UserDTO userDTO = new UserDTO();
		userDTO.setUsername(request.getUsername());
		userDTO.setPhone(request.getPhone());
		userDTO.setPassword(request.getPassword());
		userDTO.setTenantId(userDetails.getTenantId());
		R<Boolean> booleanR = remoteUserService.registerUser(userDTO);
		if (!booleanR.isOk()) {
			return R.failed(booleanR.getMsg());
		}
		return R.ok(StrUtil.format("{} 创建用户成功 ，请访问 【系统管理】> 【用户管理】 确认数据", request.getUsername()));
	}

	/**
	 * 确认表单
	 * @param sysUserRequest 表单数据
	 * @param userDetails 用户信息
	 * @param extDetails 扩展详情
	 * @return 操作结果
	 */
	@SneakyThrows
	@Override
	public R<String> confirmForm(SysUserRequest sysUserRequest, MatrixUser userDetails,
			ChatMessageDTO.ExtDetails extDetails) {
		return R.ok(String.format(IoUtil.read(formResource.getInputStream(), Charset.defaultCharset()),
				sysUserRequest.getUsername(), sysUserRequest.getPhone(), sysUserRequest.getPassword()));
	}

	/**
	 * 提交表单方法
	 * @param confirmFormDTO 确认表单DTO
	 * @param userDetails 用户详情
	 * @param extDetails 扩展详情
	 * @return 操作结果
	 */
	@Override
	public R<String> submitForm(AiConfirmFormDTO confirmFormDTO, MatrixUser userDetails,
			ChatMessageDTO.ExtDetails extDetails) {
		SysUserRequest sysUserRequest = new SysUserRequest();
		sysUserRequest.setUsername(MapUtil.getStr(confirmFormDTO, SysUserRequest.Fields.username));
		sysUserRequest.setPhone(MapUtil.getStr(confirmFormDTO, SysUserRequest.Fields.phone));
		sysUserRequest.setPassword(MapUtil.getStr(confirmFormDTO, SysUserRequest.Fields.password));

		// 检查参数
		R<String> checkedR = checkParams(sysUserRequest, userDetails, extDetails);

		if (!checkedR.isOk()) {
			return checkedR;
		}

		// 执行处理逻辑
		return handle(sysUserRequest, userDetails, extDetails);
	}

}

/**
 * 系统用户请求参数类 用于接收AI解析后的用户创建相关参数
 */
@Data
@FieldNameConstants
@EqualsAndHashCode(callSuper = true)
class SysUserRequest extends BaseAiRequest {

	/**
	 * 用户名 只支持小写英文字母
	 */
	@FieldPrompt("用户名")
	private String username;

	/**
	 * 手机号 必须符合手机号格式
	 */
	@FieldPrompt("手机号")
	private String phone;

	/**
	 * 用户密码
	 */
	@FieldPrompt("密码")
	private String password;

}
