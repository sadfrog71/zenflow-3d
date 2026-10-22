package com.deepdimen.matrix.assistant.controller;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;
import com.deepdimen.matrix.assistant.provider.AssistantProviderCatalogService;
import com.deepdimen.matrix.assistant.provider.AssistantProviderCatalogService.ProviderDefinitionRequest;
import com.deepdimen.matrix.assistant.provider.AssistantProviderCatalogService.ProviderDiagnostic;
import com.deepdimen.matrix.assistant.provider.AssistantProviderCatalogService.TenantProviderSettingRequest;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.CatalogEntry;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.ProviderDefinition;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.TenantProviderSetting;

@RestController
@RequestMapping("/assistant/providers")
@ConditionalOnBean(AssistantIdentityResolver.class)
public class AssistantProviderAdminController {

	private final AssistantIdentityResolver identityResolver;

	private final AssistantProviderCatalogService catalogService;

	public AssistantProviderAdminController(AssistantIdentityResolver identityResolver,
			AssistantProviderCatalogService catalogService) {
		this.identityResolver = identityResolver;
		this.catalogService = catalogService;
	}

	@GetMapping("/catalog")
	@PreAuthorize("isAuthenticated()")
	public List<CatalogEntry> catalog() {
		return catalogService.catalog(identityResolver.currentIdentity());
	}

	@GetMapping("/tenant-settings")
	@PreAuthorize("@pms.hasPermission('assistant_provider_tenant_manage', 'knowledge_agent_edit')")
	public List<TenantProviderSetting> tenantSettings() {
		return catalogService.tenantSettings(identityResolver.currentIdentity());
	}

	@PutMapping("/{providerCode}/tenant-settings")
	@PreAuthorize("@pms.hasPermission('assistant_provider_tenant_manage', 'knowledge_agent_edit')")
	public TenantProviderSetting saveTenantSetting(@PathVariable("providerCode") String providerCode,
			@RequestBody TenantProviderSettingRequest request) {
		return catalogService.saveTenantSetting(identityResolver.currentIdentity(), ProviderId.of(providerCode),
				request);
	}

	@PostMapping("/{providerCode}/diagnose")
	@PreAuthorize("@pms.hasPermission('assistant_provider_tenant_manage', 'knowledge_agent_edit')")
	public ProviderDiagnostic diagnose(@PathVariable("providerCode") String providerCode) {
		return catalogService.diagnose(identityResolver.currentIdentity(), ProviderId.of(providerCode));
	}

	@GetMapping("/platform/definitions")
	@PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
	public List<ProviderDefinition> definitions() {
		return catalogService.definitions();
	}

	@PutMapping("/platform/definitions/{providerCode}")
	@PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
	public ProviderDefinition saveDefinition(@PathVariable("providerCode") String providerCode,
			@RequestBody ProviderDefinitionRequest request) {
		if (request == null || !providerCode.equals(request.providerCode())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider Code 与路径不一致");
		}
		return catalogService.saveDefinition(identityResolver.currentIdentity(), request);
	}

}
