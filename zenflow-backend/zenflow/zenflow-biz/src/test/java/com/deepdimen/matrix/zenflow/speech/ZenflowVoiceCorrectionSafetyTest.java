package com.deepdimen.matrix.zenflow.speech;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ZenflowVoiceCorrectionSafetyTest {

	@Test
	void allowsSmallLikelyRecognitionCorrections() {
		assertTrue(ZenflowVoiceCorrectionService.isSafeCorrection("我们去余香肉丝", "我们去鱼香肉丝"));
	}

	@Test
	void preservesNumbersIdentifiersAndNegation() {
		assertFalse(ZenflowVoiceCorrectionService.isSafeCorrection("不要关闭2号泵", "关闭2号泵"));
		assertFalse(ZenflowVoiceCorrectionService.isSafeCorrection("检查设备AB-12", "检查设备AB-13"));
		assertFalse(ZenflowVoiceCorrectionService.isSafeCorrection("检查设备AB-12", "检查设备ab-12"));
		assertFalse(ZenflowVoiceCorrectionService.isSafeCorrection("检查30度水温", "检查40度水温"));
	}

	@Test
	void rejectsLargeRewritesAndAddedClaims() {
		assertFalse(ZenflowVoiceCorrectionService.isSafeCorrection("我今天有一点累", "我今天状态很好并且希望继续工作"));
	}
}
