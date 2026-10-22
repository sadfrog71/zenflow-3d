package com.deepdimen.matrix.assistant.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolveRequest;
import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderDescriptor;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderStatus;
import com.deepdimen.matrix.assistant.api.AssistantContextProvider;

/**
 * Reusable Provider v2 contract. Provider modules consume this artifact with test scope
 * and supply a concrete subclass with authorized test fixtures.
 */
public abstract class AssistantContextProviderContract {

	protected abstract AssistantContextProvider provider();

	protected abstract ProviderQuery query();

	protected abstract CitationResolveRequest authorizedCitation();

	protected abstract IdentityEnvelope unauthorizedIdentity();

	@Test
	void descriptorIsTheProviderMetadataAuthority() {
		AssistantContextProvider provider = provider();
		ProviderDescriptor descriptor = provider.describe();
		assertNotNull(descriptor);
		assertEquals(AssistantContextContract.VERSION, descriptor.contractVersion());
		assertNotNull(descriptor.providerId());
		assertNotNull(descriptor.iconKey());
		assertNotNull(descriptor.capabilities());
		assertNotNull(descriptor.defaultLimits());
		assertTrue(descriptor.capabilities().readOnly(), "P0/P1 providers must be read-only");
		assertTrue(descriptor.capabilities().supportsSearch(), "Provider must support the standard search query");
		assertTrue(descriptor.capabilities().supportsCitations(), "Provider must support reauthorized citations");
		assertNotNull(descriptor.capabilities().supportedSourceTypes());
		assertNotNull(descriptor.capabilities().supportedFilters());
	}

	@Test
	void queryResultUsesTheDescribedProviderIdAndNonNullCollections() {
		AssistantContextProvider provider = provider();
		ProviderResult result = provider.query(query());
		assertNotNull(result);
		assertEquals(provider.describe().providerId(), result.provider());
		assertNotNull(result.fragments());
		assertNotNull(result.warnings());
		result.fragments().forEach(fragment -> {
			assertEquals(result.provider(), fragment.provider());
			assertNotNull(fragment.citation());
			assertEquals(result.provider(), fragment.citation().provider());
			assertNotNull(fragment.citation().routeParams());
		});
	}

	@Test
	void citationResolutionReauthorizesTheCurrentIdentity() {
		AssistantContextProvider provider = provider();
		CitationResolveRequest authorized = authorizedCitation();
		CitationResolveRequest unauthorized = new CitationResolveRequest(authorized.citation(), unauthorizedIdentity());
		assertNotEquals(ProviderStatus.OK, provider.resolve(unauthorized).status(),
				"Provider resolve must reauthorize the current identity");
	}

}
