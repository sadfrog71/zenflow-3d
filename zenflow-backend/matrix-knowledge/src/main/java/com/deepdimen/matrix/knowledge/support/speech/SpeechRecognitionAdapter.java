package com.deepdimen.matrix.knowledge.support.speech;

import com.deepdimen.matrix.knowledge.dto.VoiceTranscriptionDTO;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;

/**
 * Extension point for tenant-configured speech recognition providers.
 *
 * <p>
 * Adding a provider such as Doubao must be implemented as another Spring bean; callers
 * and the mobile client remain provider agnostic.
 * </p>
 */
public interface SpeechRecognitionAdapter {

	boolean supports(AiModelEntity model);

	VoiceTranscriptionDTO transcribe(byte[] audio, AiModelEntity model);

}
