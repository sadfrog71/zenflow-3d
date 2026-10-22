package com.deepdimen.matrix.assistant.generation;

import com.deepdimen.matrix.assistant.api.AssistantModelContract.AssistantModelRequest;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelChunk;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelResolutionRequest;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ResolvedAssistantModel;

import reactor.core.publisher.Flux;

/**
 * Native model port. Generation has exactly one shape: a stream of chunks. A blocking
 * compatibility caller may collect this stream, but must not introduce another model
 * invocation path.
 */
public interface AssistantModelGateway {

	ResolvedAssistantModel resolve(ModelResolutionRequest request);

	Flux<ModelChunk> stream(AssistantModelRequest request);

}
