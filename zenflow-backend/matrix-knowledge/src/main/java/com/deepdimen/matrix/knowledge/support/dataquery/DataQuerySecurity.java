package com.deepdimen.matrix.knowledge.support.dataquery;

import com.deepdimen.matrix.common.security.util.SecurityUtils;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Captured only from the current authenticated MATRIX principal, never request DTOs/saved
 * tokens.
 */
public final class DataQuerySecurity {

	public static ControlledQueryService.Identity identity() {
		var auth = SecurityUtils.getAuthentication();
		var user = SecurityUtils.getUser(auth);
		if (auth == null || !auth.isAuthenticated() || user == null || user.getId() == null
				|| user.getTenantId() == null)
			throw new DataQueryException("UNAUTHENTICATED");
		return new ControlledQueryService.Identity(user.getTenantId().toString(), user.getId().toString());
	}

	public static String username() {
		identity();
		return SecurityUtils.getUser().getUsername();
	}

	public record ManagementActor(ControlledQueryService.Identity identity, Set<String> permissions) {
		public ManagementActor {
			permissions = Set.copyOf(permissions);
		}

		public void requireRead() {
			boolean editor = permissions.contains("knowledge_dataquery_edit")
					|| permissions.contains("knowledge_dataquery_verify");
			boolean reviewer = permissions.contains("knowledge_dataquery_human_review")
					&& (permissions.contains("knowledge_dataquery_review")
							|| permissions.contains("knowledge_dataquery_publish"));
			if (!editor && !reviewer)
				throw new DataQueryException("MANAGEMENT_FORBIDDEN");
		}

		public void require(String capability, boolean human) {
			if (!permissions.contains("knowledge_dataquery_" + capability)
					|| (human && !permissions.contains("knowledge_dataquery_human_review")))
				throw new DataQueryException("MANAGEMENT_FORBIDDEN");
		}
	}

	public static ManagementActor managementActor() {
		var identity = identity();
		return new ManagementActor(identity,
				SecurityUtils.getAuthentication()
					.getAuthorities()
					.stream()
					.map(a -> a.getAuthority())
					.collect(Collectors.toSet()));
	}

}
