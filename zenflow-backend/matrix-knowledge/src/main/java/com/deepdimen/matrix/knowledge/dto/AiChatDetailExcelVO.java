package com.deepdimen.matrix.knowledge.dto;

import cn.hutool.core.util.StrUtil;
import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.annotation.format.DateTimeFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI问答明细Excel导出模型
 */
@Data
public class AiChatDetailExcelVO {

	@ExcelProperty(value = "提问时间", index = 0)
	@DateTimeFormat("yyyy-MM-dd HH:mm:ss")
	private LocalDateTime questionTime;

	@ExcelProperty(value = "用户名", index = 1)
	private String username;

	@ExcelProperty(value = "姓名", index = 2)
	private String name;

	@ExcelProperty(value = "部门", index = 3)
	private String deptName;

	@ExcelProperty(value = "智能体", index = 4)
	private String agentName;

	@ExcelProperty(value = "原始问题", index = 5)
	private String questionText;

	@ExcelProperty(value = "响应状态", index = 6)
	private String responseStatus;

	@ExcelProperty(value = "响应时长（ms）", index = 7)
	private Long responseTime;

	@ExcelProperty(value = "输入 Token", index = 8)
	private Long promptTokens;

	@ExcelProperty(value = "输出 Token", index = 9)
	private Long completionTokens;

	@ExcelProperty(value = "用户反馈", index = 10)
	private String evaluate;

	@ExcelProperty(value = "会话ID", index = 11)
	private String conversationId;

	@ExcelProperty(value = "消息ID", index = 12)
	private String messageId;

	public static AiChatDetailExcelVO from(AiChatDetailVO source) {
		AiChatDetailExcelVO target = new AiChatDetailExcelVO();
		target.setQuestionTime(source.getQuestionTime());
		target.setUsername(StrUtil.blankToDefault(source.getUsername(), "-"));
		target.setName(StrUtil.blankToDefault(source.getName(), "-"));
		target.setDeptName(StrUtil.blankToDefault(source.getDeptName(), "-"));
		target.setAgentName(StrUtil.blankToDefault(source.getAgentName(), "-"));
		target.setQuestionText(StrUtil.blankToDefault(source.getQuestionText(), "-"));
		target.setResponseStatus(source.getResponseStatus());
		target.setResponseTime(source.getResponseTime());
		target.setPromptTokens(source.getPromptTokens() == null ? 0L : source.getPromptTokens());
		target.setCompletionTokens(source.getCompletionTokens() == null ? 0L : source.getCompletionTokens());
		target.setEvaluate(source.getEvaluate());
		target.setConversationId(StrUtil.blankToDefault(source.getConversationId(), "-"));
		target.setMessageId(StrUtil.blankToDefault(source.getMessageId(), "-"));
		return target;
	}

}
