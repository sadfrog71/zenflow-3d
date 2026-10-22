package com.deepdimen.matrix.knowledge.support.function;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.component.MatrixCustomOpaqueTokenIntrospector;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.security.util.NonWebTokenContextHolder;
import com.deepdimen.matrix.knowledge.dto.AiConfirmFormDTO;
import com.deepdimen.matrix.knowledge.dto.BaseAiRequest;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.entity.AiChatRecordEntity;
import com.deepdimen.matrix.knowledge.mapper.AiChatRecordMapper;
import com.deepdimen.matrix.knowledge.support.util.ChatMessageContextHolder;
import com.deepdimen.matrix.knowledge.support.util.JSONRepairUtil;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import com.deepdimen.matrix.knowledge.support.util.ToolSpecificationsUtils;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Map;
import java.util.Objects;

import static com.deepdimen.matrix.knowledge.support.constant.AiPromptField.systemTime;

/**
 * 函数调用基础接口
 *
 * 该接口定义了AI函数调用的核心功能，包括： 1. 函数的展示配置 2. 函数的描述信息生成 3. 参数验证 4. 业务处理逻辑 5.
 * 工具规格(ToolSpecification)的构建
 *
 * 所有实现此接口的类都可以作为一个AI函数被大模型调用
 *
 * @author caoyuan
 * @date 2024/3/29
 */
public interface FunctionCalling<T extends BaseAiRequest> extends ToolExecutor {

	/** 确认表单类型 */
	int CONFIRM_FORM = 9;

	/**
	 * 是否在前端展示此函数 默认返回true，表示该函数会在前端功能列表中显示
	 * @return boolean true-展示，false-隐藏
	 */
	default boolean showFunction() {
		return true;
	}

	/**
	 * 显示名称 返回在前端展示的函数名称和描述信息 默认返回函数名称
	 * @return {@link String } 函数的显示信息
	 */
	default String showInfo() {
		return this.functionName();
	}

	/**
	 * 函数名称 返回函数的唯一标识符 默认使用类名作为函数名
	 * @return String 函数名称
	 */
	default String functionName() {
		return this.getClass().getSimpleName();
	}

	/**
	 * 路由路径 点击函数卡片后跳转的页面路径 默认返回空字符串，表示不跳转
	 * @return {@link String } 跳转路径
	 */
	default String routePath() {
		return "";
	}

	/**
	 * 函数描述 生成函数的详细描述，用于告诉AI此函数的作用和使用场景 默认会注入以下信息： - 租户ID - 系统时间 - 消息key
	 * @return String 函数描述信息
	 */
	default String functionDesc() {
		String functionDesc = PromptBuilder.render(String.format("%s.st", this.functionName()), Map.of("tenantId",
				Objects.nonNull(TenantContextHolder.getTenantId()) ? TenantContextHolder.getTenantId() : StrUtil.EMPTY,
				"systemTime", DateUtil.now()));
		String metadata = PromptBuilder
			.render("knowledge-func-metadata.st",
					Map.of(BaseAiRequest.Fields.messageKey,
							Objects.nonNull(ChatMessageContextHolder.get())
									? ChatMessageContextHolder.get().getMessageKey() : StrUtil.EMPTY,
							systemTime, DateUtil.now()));
		return functionDesc + metadata;
	}

	/**
	 * 构建工具规格 将函数转换为大模型可以理解的ToolSpecification 包括函数名、描述和参数定义
	 * @return {@link ToolSpecification } 工具规格对象
	 */
	default ToolSpecification buildToolSpecification() {
		ToolSpecification.Builder builder = ToolSpecification.builder();
		builder.name(this.functionName());
		builder.description(this.functionDesc());

		Field[] fields = ReflectUtil.getFields(getGenericType());
		builder.parameters(ToolSpecificationsUtils.parametersFrom(fields));
		return builder.build();
	}

	/**
	 * 大模型回调执行入口 当AI决定调用此函数时，会通过此方法执行 该方法会调用preHandle进行预处理
	 * @param toolExecutionRequest 工具执行请求，包含函数参数
	 * @param memoryId 内存ID（用于多轮对话的上下文管理）
	 * @return {@link String } 执行结果
	 */
	@Override
	default String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
		String correctJSON = JSONRepairUtil.repair(toolExecutionRequest.arguments());
		R<String> result = preHandle(JSONUtil.toBean(correctJSON, getGenericType()));
		return StrUtil.isBlank(result.getData()) ? result.getMsg() : result.getData();
	}

	/**
	 * 大模型回调执行入口（重载方法） 返回R对象，包含成功/失败状态和数据
	 * @param toolExecutionRequest 工具执行请求
	 * @return {@link R } 执行结果
	 */
	default R execute(ToolExecutionRequest toolExecutionRequest) {
		String correctJSON = JSONRepairUtil.repair(toolExecutionRequest.arguments());
		return preHandle(JSONUtil.toBean(correctJSON, getGenericType()));
	}

	/**
	 * 检查参数是否正确 子类必须实现此方法，用于验证输入参数的合法性
	 * @param t 请求信息，包含函数参数
	 * @param userDetails 用户信息
	 * @param extDetails 扩展信息
	 * @return R 参数检查结果
	 */
	R checkParams(T t, MatrixUser userDetails, ChatMessageDTO.ExtDetails extDetails);

	/**
	 * 获取泛型类型 通过反射获取实现类的泛型参数类型
	 * @return Class<T> 泛型参数的实际类型
	 */
	default Class<T> getGenericType() {
		return (Class<T>) ((ParameterizedType) getClass().getGenericInterfaces()[0]).getActualTypeArguments()[0];
	}

	/**
	 * 预处理 在执行业务处理前的统一预处理逻辑： 1. 验证messageKey是否存在 2. 查询聊天记录 3. 设置上下文信息（租户、用户、token等） 4.
	 * 调用参数检查 5. 调用业务处理
	 * @param t 请求参数
	 * @return R 处理结果
	 */
	default R preHandle(T t) {
		if (Objects.isNull(t.getMessageKey())) {
			return R.failed("创建失败，系统内部错误");
		}

		AiChatRecordMapper chatRecordMapper = SpringUtil.getBean(AiChatRecordMapper.class);
		AiChatRecordEntity chatRecordEntity = chatRecordMapper.selectById(Long.parseLong(t.getMessageKey()));

		if (Objects.isNull(chatRecordEntity) || StrUtil.isBlank(chatRecordEntity.getExtDetails())) {
			return R.failed("创建失败，系统内部错误");
		}

		ChatMessageDTO.ExtDetails extDetails = JSONUtil.toBean(chatRecordEntity.getExtDetails(),
				ChatMessageDTO.ExtDetails.class);

		ChatMessageDTO chatMessageDTO = new ChatMessageDTO();
		chatMessageDTO.setContent(chatRecordEntity.getQuestionText());
		chatMessageDTO.setExtDetails(extDetails);
		chatMessageDTO.setConversationId(chatRecordEntity.getConversationId());
		chatMessageDTO.setModelName(chatRecordEntity.getModelName());
		ChatMessageContextHolder.set(chatMessageDTO);

		String accessToken = extDetails.getAccessToken();
		Long tenantId = extDetails.getTenantId();

		// 获取上下文的用户、租户
		TenantContextHolder.setTenantId(tenantId);
		MatrixCustomOpaqueTokenIntrospector opaqueTokenIntrospector = SpringUtil
			.getBean(MatrixCustomOpaqueTokenIntrospector.class);
		NonWebTokenContextHolder.setToken(accessToken);

		try {
			MatrixUser userDetails = (MatrixUser) opaqueTokenIntrospector.introspect(accessToken);

			// 增加confirm 逻辑
			R<String> confirmedR = confirmForm(t, userDetails, extDetails);
			if (confirmedR.isOk()) {
				confirmedR.setCode(CONFIRM_FORM);
				return confirmedR;
			}

			R result = checkParams(t, userDetails, extDetails);
			// 没有校验失败返回 R error
			if (!result.isOk()) {
				return result;
			}
			return handle(t, userDetails, extDetails);
		}
		catch (Exception e) {
			return R.failed(StrUtil.format("创建失败，请重试 {}", e.getMessage()));
		}
		finally {
			NonWebTokenContextHolder.clear();
			TenantContextHolder.clear();
		}
	}

	/**
	 * 业务处理逻辑 子类必须实现此方法，完成具体的业务功能
	 * @param t 请求参数
	 * @param userDetails 用户详细信息
	 * @param extDetails 扩展详细信息
	 * @return {@link R }<{@link String }> 业务处理结果
	 */
	R<String> handle(T t, MatrixUser userDetails, ChatMessageDTO.ExtDetails extDetails);

	/**
	 * 确认表单
	 * @param t 表单数据
	 * @param userDetails 用户信息
	 * @param extDetails 扩展详情
	 * @return 操作结果
	 */
	default R<String> confirmForm(T t, MatrixUser userDetails, ChatMessageDTO.ExtDetails extDetails) {
		return R.failed();
	};

	/**
	 * 提交表单方法
	 * @param confirmFormDTO 确认表单DTO
	 * @param userDetails 用户详情
	 * @param extDetails 扩展详情
	 * @return 操作结果
	 */
	default R<String> submitForm(AiConfirmFormDTO confirmFormDTO, MatrixUser userDetails,
			ChatMessageDTO.ExtDetails extDetails) {
		return R.ok("未实现提交表单方法，请重写");
	}

}
