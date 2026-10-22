package com.deepdimen.matrix.knowledge.support.dataquery;

/**
 * Default four-eyes review; an explicit test-only tenant:user exception, never a role
 * wildcard.
 */
public record DataQueryReviewPolicy(String tenantId, String userId) {

	public static final DataQueryReviewPolicy INDEPENDENT = new DataQueryReviewPolicy("0", "0");

	public static DataQueryReviewPolicy testPrincipal(String principal) {
		if (principal == null || principal.isBlank())
			return INDEPENDENT;
		if (!principal.matches("[1-9][0-9]{0,18}:[1-9][0-9]{0,18}"))
			throw new IllegalArgumentException("Invalid test self-review principal");
		String[] pair = principal.split(":");
		Long.parseLong(pair[0]);
		Long.parseLong(pair[1]);
		return new DataQueryReviewPolicy(pair[0], pair[1]);
	}

	public boolean allows(ControlledQueryService.Identity identity) {
		return !"0".equals(tenantId) && tenantId.equals(identity.tenantId()) && userId.equals(identity.userId());
	}
}
