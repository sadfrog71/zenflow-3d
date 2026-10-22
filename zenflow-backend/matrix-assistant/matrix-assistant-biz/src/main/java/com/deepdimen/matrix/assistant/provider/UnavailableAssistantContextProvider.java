package com.deepdimen.matrix.assistant.provider;

import java.util.List;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderCapabilities;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderDescriptor;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderLimits;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantContextProvider;

final class UnavailableAssistantContextProvider implements AssistantContextProvider {

	private final ProviderId providerId;

	UnavailableAssistantContextProvider(ProviderId providerId) {
		this.providerId = providerId;
	}

	@Override
	public ProviderDescriptor describe() {
		return new ProviderDescriptor(com.deepdimen.matrix.assistant.api.AssistantContextContract.VERSION, providerId,
				providerId.value(), "Provider is not wired", "generic",
				new ProviderCapabilities(true, true, true, true, true, false, false, List.of(), List.of()),
				new ProviderLimits(20, 12000, 3000));
	}

	@Override
	public ProviderResult query(ProviderQuery query) {
		return ProviderResult.unavailable(providerId, providerId + " Provider 尚未由领域模块接线，未返回任何示例数据。");
	}

}
