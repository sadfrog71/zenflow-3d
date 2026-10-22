package com.deepdimen.matrix.assistant.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.assistant.api.AssistantSessionContract.IntentDisposition;

class AssistantIntentPolicyTest {

	private final AssistantIntentPolicy policy = new AssistantIntentPolicy();

	@Test
	void treatsAuthorizedScopeQuestionAsReadOnly() {
		assertEquals(IntentDisposition.READ_ONLY,
				policy.classify("请基于当前授权范围内的资料总结资讯").disposition());
	}

	@Test
	void stillBlocksPermissionChangingActions() {
		assertEquals(IntentDisposition.ACTION_NOT_SUPPORTED,
				policy.classify("请给该用户新增授权").disposition());
		assertEquals(IntentDisposition.ACTION_NOT_SUPPORTED,
				policy.classify("请取消授权并变更权限").disposition());
	}

}
