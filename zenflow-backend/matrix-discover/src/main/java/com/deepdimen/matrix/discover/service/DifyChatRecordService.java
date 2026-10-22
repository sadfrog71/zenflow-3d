package com.deepdimen.matrix.discover.service;

/**
 * Dify 对话记录异步写入服务
 */
public interface DifyChatRecordService {

	/**
	 * 异步写入对话记录、账单并递增智能体使用次数
	 * @param context 对话上下文（请求阶段收集）
	 * @param result 对话结果（响应阶段收集）
	 */
	void asyncSaveChatRecord(DifyChatContext context, DifyChatResult result);

	/**
	 * 异步更新评价（feedbacks 旁路回写）
	 * @param messageId Dify 消息ID
	 * @param evaluate 评价类型: like/dislike/null(取消)
	 */
	void asyncUpdateEvaluate(Long tenantId, String messageId, String evaluate);

	/**
	 * 对话请求上下文（在主线程中收集，传入异步方法）
	 */
	record DifyChatContext(Long agentId, String username, String query, String ip, String userAgent, long startTime,
			Long tenantId, boolean newConversation, String sessionInputs) {
	}

	/**
	 * 对话响应结果（从 SSE/blocking 响应中解析）
	 */
	record DifyChatResult(String answer, String conversationId, String messageId, String model, Long promptTokens,
			Long completionTokens, Long totalTokens) {
	}

}
