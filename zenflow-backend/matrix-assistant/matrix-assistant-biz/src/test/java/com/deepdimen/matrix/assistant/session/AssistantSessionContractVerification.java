package com.deepdimen.matrix.assistant.session;

import java.util.List;
import java.util.UUID;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderCapabilities;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderDescriptor;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderLimits;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantContextProvider;
import com.deepdimen.matrix.assistant.api.AssistantProposalContract.ProposalStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.CreateSessionRequest;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.IntentDisposition;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageExchange;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageRole;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.SendMessageRequest;
import com.deepdimen.matrix.assistant.capability.UnavailableAssistantPromptSkillCatalog;
import com.deepdimen.matrix.assistant.generation.AssistantModelGateway;
import com.deepdimen.matrix.assistant.generation.AssistantNativeProfileResolver;
import com.deepdimen.matrix.assistant.orchestrator.AssistantContextOrchestrator;
import com.deepdimen.matrix.assistant.policy.AssistantIntentPolicy;
import com.deepdimen.matrix.assistant.provider.AssistantProviderRegistry;
import com.deepdimen.matrix.assistant.run.AssistantPromptComposer;
import com.deepdimen.matrix.assistant.run.AssistantRunDispatchService;
import com.deepdimen.matrix.assistant.run.AssistantRunEngine;
import com.deepdimen.matrix.assistant.run.AssistantRunEventService;
import com.deepdimen.matrix.assistant.run.InMemoryAssistantRunStore;

/** Offline executable verification; it has no test-framework dependency. */
public final class AssistantSessionContractVerification {

	private AssistantSessionContractVerification() {
	}

	public static void main(String[] args) {
		IdentityEnvelope identity = new IdentityEnvelope("tenant-1", "user-1", "org-1", List.of("org-1"),
				List.of("role-1"), List.of("dept-1"), "correlation-1", "assistant-chat");
		List<AssistantContextProvider> providers = List
			.of(ProviderId.KNOWLEDGE, ProviderId.WORK, ProviderId.CAPSULE, ProviderId.INFORMATION)
			.stream()
			.map(AssistantSessionContractVerification::unavailableProvider)
			.toList();
		AssistantContextOrchestrator orchestrator = new AssistantContextOrchestrator(
				new AssistantProviderRegistry(providers));
		InMemoryAssistantSessionRepository repository = new InMemoryAssistantSessionRepository();
		InMemoryAssistantRunStore runStore = new InMemoryAssistantRunStore(repository);
		AssistantModelGateway gateway = new VerificationModelGateway();
		AssistantNativeProfileResolver profileResolver = (runId, currentIdentity, requireStreaming, classification,
				containsContext) -> new AssistantNativeProfileResolver.ResolvedNativeProfile("1", "verification", null,
						gateway.resolve(
								new com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelResolutionRequest(
										com.deepdimen.matrix.assistant.api.AssistantModelContract.VERSION, runId,
										currentIdentity, null, requireStreaming, classification, containsContext)));
		AssistantRunEngine runEngine = new AssistantRunEngine(runStore, repository,
				new AssistantRunEventService(runStore), new AssistantRunDispatchService(), orchestrator,
				new UnavailableAssistantPromptSkillCatalog(), profileResolver, gateway, new AssistantIntentPolicy(),
				new AssistantPromptComposer());
		AssistantSessionService service = new AssistantSessionService(repository, orchestrator, runEngine);

		List<ProviderId> selectedProviders = List.of(ProviderId.KNOWLEDGE, ProviderId.WORK, ProviderId.CAPSULE,
				ProviderId.INFORMATION);
		String sessionId = service.create(identity, new CreateSessionRequest("测试", selectedProviders, "/home"))
			.assistantSessionId();
		String readOnlyClientMessageId = UUID.randomUUID().toString();
		SendMessageRequest readOnlyRequest = new SendMessageRequest(readOnlyClientMessageId, "今天有哪些待办？", null, null,
				List.of());
		MessageExchange readOnly = service.send(identity, sessionId, readOnlyRequest);
		require(readOnly.disposition() == IntentDisposition.READ_ONLY, "read-only disposition");
		require(readOnly.contextPack() != null, "context pack");
		require(readOnly.providerResults().size() == 4, "four explicit providers");
		require(readOnly.responseMessage().role() == MessageRole.SYSTEM_NOTICE, "honest unavailable response");
		MessageExchange repeated = service.send(identity, sessionId, readOnlyRequest);
		require(repeated.responseMessage().messageId().equals(readOnly.responseMessage().messageId()),
				"idempotent exchange response");
		require(service.messages(identity, sessionId).size() == 2, "idempotent exchange message count");

		MessageExchange write = service.send(identity, sessionId,
				new SendMessageRequest(UUID.randomUUID().toString(), "创建任务：复核方案", List.of(), null, List.of()));
		require(write.disposition() == IntentDisposition.PROPOSAL_ONLY_DISABLED, "proposal-only disposition");
		require(write.proposal() != null && write.proposal().status() == ProposalStatus.DISABLED, "disabled proposal");
		require(write.contextPack() == null, "write intent must not query providers");
	}

	private static AssistantContextProvider unavailableProvider(ProviderId providerId) {
		return new AssistantContextProvider() {
			@Override
			public ProviderDescriptor describe() {
				return new ProviderDescriptor(com.deepdimen.matrix.assistant.api.AssistantContextContract.VERSION,
						providerId, providerId.value(), "not wired", "generic",
						new ProviderCapabilities(true, true, true, true, true, false, false, List.of(), List.of()),
						new ProviderLimits(20, 12000, 3000));
			}

			@Override
			public ProviderResult query(ProviderQuery request) {
				return ProviderResult.unavailable(providerId, "not wired");
			}
		};
	}

	private static final class VerificationModelGateway implements AssistantModelGateway {

		@Override
		public com.deepdimen.matrix.assistant.api.AssistantModelContract.ResolvedAssistantModel resolve(
				com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelResolutionRequest request) {
			return new com.deepdimen.matrix.assistant.api.AssistantModelContract.ResolvedAssistantModel(
					com.deepdimen.matrix.assistant.api.AssistantModelContract.VERSION, "1", "verification", "local",
					new com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelCapabilities(false, 8192, 512,
							true),
					new com.deepdimen.matrix.assistant.api.AssistantModelContract.AssistantEgress(
							com.deepdimen.matrix.assistant.api.AssistantModelContract.DeploymentDomain.INTERNAL,
							"local", true,
							com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification.SENSITIVE,
							"test"));
		}

		@Override
		public reactor.core.publisher.Flux<com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelChunk> stream(
				com.deepdimen.matrix.assistant.api.AssistantModelContract.AssistantModelRequest request) {
			return reactor.core.publisher.Flux.just(
					new com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelChunk(request.runId(), 0,
							"verification", false, ""),
					new com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelChunk(request.runId(), 1, "",
							true, "STOP"));
		}

	}

	private static void require(boolean condition, String description) {
		if (!condition) {
			throw new IllegalStateException("Verification failed: " + description);
		}
	}

}
