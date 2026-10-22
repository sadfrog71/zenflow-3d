package com.deepdimen.matrix.discover.service.impl;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.discover.entity.DifyBillEntity;
import com.deepdimen.matrix.discover.entity.DifyChatRecordEntity;
import com.deepdimen.matrix.discover.mapper.DifyBillMapper;
import com.deepdimen.matrix.discover.mapper.DifyChatRecordMapper;
import com.deepdimen.matrix.discover.service.DifyChatRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DifyChatRecordServiceImpl implements DifyChatRecordService {

	private final DifyChatRecordMapper chatRecordMapper;

	private final DifyBillMapper billMapper;

	@Async
	@Override
	public void asyncSaveChatRecord(DifyChatContext context, DifyChatResult result) {
		try {
			// 手动设置租户上下文（异步线程中可能丢失）
			if (context.tenantId() != null) {
				TenantContextHolder.setTenantId(context.tenantId());
			}

			long useTime = System.currentTimeMillis() - context.startTime();

			// 写入 ai_chat_record
			DifyChatRecordEntity record = new DifyChatRecordEntity();
			record.setAgentId(context.agentId());
			record.setUsername(context.username());
			record.setQuestionText(context.query());
			record.setIp(context.ip());
			record.setUserAgent(context.userAgent());
			record.setAnswerText(result.answer());
			record.setConversationId(result.conversationId());
			record.setMessageId(result.messageId());
			record.setModelName(result.model());
			record.setSessionInputs(context.sessionInputs());
			record.setLlmFlag("1");
			record.setInnerFlag("1");
			record.setUseTime(useTime);
			record.setCreateBy(context.username());
			record.setUpdateBy(context.username());
			chatRecordMapper.insert(record);

			// 写入 ai_bill
			if (result.totalTokens() != null && result.totalTokens() > 0) {
				DifyBillEntity bill = new DifyBillEntity();
				bill.setMessageKey(record.getRecordId());
				bill.setPromptTokens(result.promptTokens());
				bill.setCompletionTokens(result.completionTokens());
				bill.setTokens(result.totalTokens());
				bill.setModel(result.model());
				bill.setIp(context.ip());
				bill.setTokenType("0");
				bill.setUseTime(useTime);
				bill.setCreateBy(context.username());
				bill.setUpdateBy(context.username());
				billMapper.insert(bill);
			}

			// 仅新会话时递增 ai_agent.use_count（续聊不重复计数）
			if (context.newConversation()) {
				chatRecordMapper.incrementAgentUseCount(context.agentId());
			}

			log.debug("[Dify Record] 对话记录写入成功, agentId={}, recordId={}", context.agentId(), record.getRecordId());
		}
		catch (Exception e) {
			log.error("[Dify Record] 对话记录写入失败, agentId={}", context.agentId(), e);
		}
		finally {
			TenantContextHolder.clear();
		}
	}

	@Async
	@Override
	public void asyncUpdateEvaluate(Long tenantId, String messageId, String evaluate) {
		try {
			if (tenantId == null) {
				throw new IllegalArgumentException("评价回写缺少租户上下文");
			}
			TenantContextHolder.setTenantId(tenantId);
			chatRecordMapper.updateEvaluateByMessageId(tenantId, messageId, evaluate);
			log.debug("[Dify Record] 评价回写成功, messageId={}, evaluate={}", messageId, evaluate);
		}
		catch (Exception e) {
			log.error("[Dify Record] 评价回写失败, messageId={}", messageId, e);
		}
		finally {
			TenantContextHolder.clear();
		}
	}

}
