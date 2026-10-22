package com.deepdimen.matrix.assistant.orchestrator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.Citation;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ContextFragment;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolution;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolveRequest;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderLimits;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderScope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderStatus;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderWarning;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantContextPack;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.ScopeHint;
import com.deepdimen.matrix.assistant.provider.AssistantProviderRegistry;

@Service
public class AssistantContextOrchestrator {

	private final AssistantProviderRegistry registry;

	private final ExecutorService providerExecutor;

	public AssistantContextOrchestrator(AssistantProviderRegistry registry) {
		this(registry, AssistantProviderExecutionConfiguration.sharedTestExecutor());
	}

	@Autowired
	public AssistantContextOrchestrator(AssistantProviderRegistry registry,
			@Qualifier("assistantProviderExecutor") ExecutorService providerExecutor) {
		this.registry = registry;
		this.providerExecutor = providerExecutor;
	}

	public List<ProviderId> providerIds() {
		return registry.providerIds();
	}

	public List<ProviderId> defaultProviderIds(IdentityEnvelope identity) {
		return registry.defaultProviderIds(identity);
	}

	public List<ProviderId> validateProviderSelection(IdentityEnvelope identity, List<ProviderId> selected) {
		Map<ProviderId, com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.CatalogEntry> catalog = new LinkedHashMap<>();
		registry.catalog(identity).forEach(entry -> catalog.put(entry.providerId(), entry));
		for (ProviderId providerId : selected) {
			var entry = catalog.get(providerId);
			if (entry == null) {
				throw new AssistantProviderRegistry.ProviderNotRegisteredException(providerId);
			}
			if (!entry.enabled()) {
				throw new IllegalArgumentException("PROVIDER_NOT_ENABLED: " + providerId);
			}
		}
		return List.copyOf(selected);
	}

	public AssistantContextPack buildContextPack(IdentityEnvelope identity, String sessionId, String messageId,
			String query, List<ProviderId> requestedScopes, ScopeHint scopeHint) {
		return buildContextPack(identity, sessionId, messageId, messageId, query, requestedScopes, scopeHint,
				List.of());
	}

	public AssistantContextPack buildContextPack(IdentityEnvelope identity, String sessionId, String messageId,
			String query, List<ProviderId> requestedScopes, ScopeHint scopeHint, List<String> previousUserQueries) {
		return buildContextPack(identity, sessionId, messageId, messageId, query, requestedScopes, scopeHint,
				previousUserQueries);
	}

	public AssistantContextPack buildContextPack(IdentityEnvelope identity, String sessionId, String messageId,
			String runId, String query, List<ProviderId> requestedScopes, ScopeHint scopeHint,
			List<String> previousUserQueries) {
		List<ProviderId> scopes = requestedScopes == null ? registry.defaultProviderIds(identity)
				: List.copyOf(requestedScopes);
		ProviderScope scope = new ProviderScope(null, null, scopeHint == null ? List.of() : scopeHint.objectIds(),
				scopeHint == null ? null : scopeHint.sourceRoute(), scopeHint == null ? null : scopeHint.filters());
		List<String> queryTerms = scope.explicitObjectIds().isEmpty()
				? AssistantQueryPlanner.plan(query, previousUserQueries) : List.of();
		List<PendingSearch> pendingSearches = scopes.stream()
			.map(providerId -> submit(providerId, identity, runId, scope, query, queryTerms,
					AssistantQueryPlanner.limitsFor(providerId.value())))
			.toList();
		List<ProviderResult> providerResults = new ArrayList<>();
		List<ContextFragment> fragments = new ArrayList<>();
		List<ProviderWarning> warnings = new ArrayList<>();
		for (PendingSearch pending : pendingSearches) {
			ProviderResult result = await(pending);
			providerResults.add(result);
			fragments.addAll(result.fragments());
			warnings.addAll(result.warnings());
		}
		Instant createdAt = Instant.now();
		return new AssistantContextPack(UUID.randomUUID().toString(), sessionId, messageId, createdAt,
				createdAt.plus(Duration.ofMinutes(30)), fingerprint(identity), providerResults, fragments, warnings);
	}

	/** Executes the exact ProviderQuery objects frozen in a validated semantic plan. */
	public AssistantContextPack buildContextPack(IdentityEnvelope identity, String sessionId, String messageId,
			Map<ProviderId, ProviderQuery> plannedQueries) {
		Map<ProviderId, ProviderQuery> queries = plannedQueries == null ? Map.of() : Map.copyOf(plannedQueries);
		List<PendingSearch> pendingSearches = queries.entrySet().stream().map(entry -> {
			ProviderQuery query = entry.getValue();
			if (query == null || query.identity() == null || !identity.tenantId().equals(query.identity().tenantId())
					|| !identity.userId().equals(query.identity().userId())) {
				throw new IllegalArgumentException("PLANNED_PROVIDER_IDENTITY_MISMATCH");
			}
			return submit(entry.getKey(), query);
		}).toList();
		List<ProviderResult> providerResults = new ArrayList<>();
		List<ContextFragment> fragments = new ArrayList<>();
		List<ProviderWarning> warnings = new ArrayList<>();
		for (PendingSearch pending : pendingSearches) {
			ProviderResult result = await(pending);
			providerResults.add(result);
			fragments.addAll(result.fragments());
			warnings.addAll(result.warnings());
		}
		Instant createdAt = Instant.now();
		return new AssistantContextPack(UUID.randomUUID().toString(), sessionId, messageId, createdAt,
				createdAt.plus(Duration.ofMinutes(30)), fingerprint(identity), providerResults, fragments, warnings);
	}

	private PendingSearch submit(ProviderId providerId, ProviderQuery query) {
		long timeoutMs = registry.queryTimeoutMs(providerId, query.identity(), query.limits());
		long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
		try {
			Future<ProviderResult> future = providerExecutor.submit(() -> {
				try {
					return registry.query(providerId, query);
				}
				catch (AssistantProviderRegistry.ProviderNotRegisteredException ex) {
					return ProviderResult.notRegistered(providerId);
				}
				catch (RuntimeException ex) {
					return ProviderResult.unavailable(providerId, "Provider 调用失败，已按不可用降级。");
				}
			});
			return new PendingSearch(providerId, future, null, deadlineNanos);
		}
		catch (RejectedExecutionException ex) {
			return new PendingSearch(providerId, null,
					ProviderResult.unavailable(providerId, "Provider 执行队列已满，请稍后重试。"), deadlineNanos);
		}
	}

	private PendingSearch submit(ProviderId providerId, IdentityEnvelope identity, String runId, ProviderScope scope,
			String originalQuery, List<String> queryTerms, ProviderLimits requestedLimits) {
		long timeoutMs = registry.queryTimeoutMs(providerId, identity, requestedLimits);
		long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
		try {
			Future<ProviderResult> future = providerExecutor
				.submit(() -> search(providerId, identity, runId, scope, originalQuery, queryTerms, requestedLimits));
			return new PendingSearch(providerId, future, null, deadlineNanos);
		}
		catch (RejectedExecutionException ex) {
			return new PendingSearch(providerId, null,
					ProviderResult.unavailable(providerId, "Provider 执行队列已满，请稍后重试。"), deadlineNanos);
		}
	}

	private ProviderResult await(PendingSearch pending) {
		if (pending.immediate() != null) {
			return pending.immediate();
		}
		if (pending.future().isDone()) {
			return completed(pending);
		}
		long remaining = pending.deadlineNanos() - System.nanoTime();
		if (remaining <= 0) {
			pending.future().cancel(true);
			return ProviderResult.unavailable(pending.providerId(), "Provider 查询超时。");
		}
		try {
			return pending.future().get(remaining, TimeUnit.NANOSECONDS);
		}
		catch (TimeoutException ex) {
			pending.future().cancel(true);
			return ProviderResult.unavailable(pending.providerId(), "Provider 查询超时。");
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			pending.future().cancel(true);
			return ProviderResult.unavailable(pending.providerId(), "Provider 查询被中断。");
		}
		catch (ExecutionException ex) {
			return ProviderResult.unavailable(pending.providerId(), "Provider 调用失败，已按不可用降级。");
		}
	}

	private ProviderResult completed(PendingSearch pending) {
		try {
			return pending.future().get();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return ProviderResult.unavailable(pending.providerId(), "Provider 查询被中断。");
		}
		catch (ExecutionException ex) {
			return ProviderResult.unavailable(pending.providerId(), "Provider 调用失败，已按不可用降级。");
		}
	}

	private ProviderResult search(ProviderId providerId, IdentityEnvelope identity, String runId, ProviderScope scope,
			String originalQuery, List<String> queryTerms, ProviderLimits requestedLimits) {
		List<String> variants = queryTerms.isEmpty()
				? List.of(AssistantQueryPlanner.browseQuery(providerId.value(), originalQuery)) : queryTerms;
		List<ProviderResult> results = new ArrayList<>();
		for (String queryTerm : variants) {
			ProviderQuery request = new ProviderQuery(AssistantContextContract.VERSION, UUID.randomUUID().toString(),
					runId, identity.correlationId(), identity, queryTerm, scope, requestedLimits,
					variants);
			try {
				results.add(registry.query(providerId, request));
			}
			catch (AssistantProviderRegistry.ProviderNotRegisteredException ex) {
				results.add(ProviderResult.notRegistered(providerId));
			}
			catch (RuntimeException ex) {
				results.add(ProviderResult.unavailable(providerId, "Provider 调用失败，已按不可用降级。"));
			}
		}
		return merge(providerId, results);
	}

	private record PendingSearch(ProviderId providerId, Future<ProviderResult> future, ProviderResult immediate,
			long deadlineNanos) {
	}

	private ProviderResult merge(ProviderId providerId, List<ProviderResult> results) {
		if (results.isEmpty()) {
			return ProviderResult.unavailable(providerId, "Provider 未返回结果。");
		}
		Map<String, ContextFragment> uniqueFragments = new LinkedHashMap<>();
		List<ProviderWarning> warnings = new ArrayList<>();
		int permissionFiltered = 0;
		boolean truncated = false;
		boolean hasAvailable = false;
		boolean hasUnavailable = false;
		boolean hasForbidden = false;
		for (ProviderResult result : results) {
			result.fragments().forEach(fragment -> uniqueFragments.putIfAbsent(fragment.fragmentId(), fragment));
			warnings.addAll(result.warnings());
			permissionFiltered += result.permissionFiltered();
			truncated |= result.truncated();
			switch (result.status()) {
				case OK, PARTIAL -> hasAvailable = true;
				case UNAVAILABLE -> hasUnavailable = true;
				case FORBIDDEN_SCOPE -> hasForbidden = true;
				case NO_MATCH -> {
					// A query variant without a hit is normal when a request contains
					// multiple business terms; it does not make the provider partial.
				}
			}
		}
		ProviderStatus status = hasAvailable
				? (hasUnavailable || hasForbidden ? ProviderStatus.PARTIAL : ProviderStatus.OK)
				: hasForbidden ? ProviderStatus.FORBIDDEN_SCOPE
						: hasUnavailable ? ProviderStatus.UNAVAILABLE : ProviderStatus.NO_MATCH;
		List<ContextFragment> bounded = uniqueFragments.values().stream().limit(20).toList();
		return new ProviderResult(AssistantContextContract.VERSION, providerId, status, Instant.now(), bounded.size(),
				permissionFiltered, truncated || uniqueFragments.size() > bounded.size(), bounded, warnings);
	}

	public CitationResolution resolveCitation(IdentityEnvelope identity, Citation citation) {
		return registry.resolve(new CitationResolveRequest(citation, identity));
	}

	public String fingerprint(IdentityEnvelope identity) {
		String canonical = String.join("|", safe(identity.tenantId()), safe(identity.subjectType()), safe(identity.userId()),
				safe(identity.currentOrgId()), String.join(",", identity.accessibleOrgIds()),
				String.join(",", identity.roleIds()), String.join(",", identity.deptIds()));
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}

}
