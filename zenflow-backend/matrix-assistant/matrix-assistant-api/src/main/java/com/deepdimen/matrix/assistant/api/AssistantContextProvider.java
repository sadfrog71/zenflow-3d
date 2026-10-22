package com.deepdimen.matrix.assistant.api;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolution;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolveRequest;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderDescriptor;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;

public interface AssistantContextProvider {

	ProviderDescriptor describe();

	ProviderResult query(ProviderQuery query);

	default CitationResolution resolve(CitationResolveRequest request) {
		return CitationResolution.forbidden(request.citationId(),
				"Citation resolver must reauthorize the current identity");
	}

}
