package com.deepdimen.matrix.discover.assistant;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;

/**
 * Final cut-over gate composed by U8 after Provider, Skill and golden-set diagnostics.
 */
public interface NativeAssistantCutoverGate {

	void assertReady(IdentityEnvelope identity, Long nativeProfileId);

}
