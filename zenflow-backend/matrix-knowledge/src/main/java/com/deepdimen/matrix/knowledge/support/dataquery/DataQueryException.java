package com.deepdimen.matrix.knowledge.support.dataquery;

/**
 * Safe error code and optional owned audit reference; never SQL, credentials or driver
 * messages.
 */
public final class DataQueryException extends RuntimeException {

	private final String queryId;

	private final String ruleVersion;

	private java.util.Map<String, Object> databaseDiagnostic = java.util.Map.of();

	public DataQueryException(String code) {
		this(code, null, null);
	}

	public DataQueryException(String code, String queryId, String ruleVersion) {
		super(code);
		this.queryId = queryId;
		this.ruleVersion = ruleVersion;
	}

	DataQueryException databaseError(java.sql.SQLException error) {
		String state = error.getSQLState();
		databaseDiagnostic = java.util.Map.of("sqlState",
				state != null && state.matches("[A-Z0-9]{5}") ? state : "UNKNOWN", "vendorCode", error.getErrorCode());
		return this;
	}

	java.util.Map<String, Object> databaseDiagnostic() {
		return databaseDiagnostic;
	}

	public String queryId() {
		return queryId;
	}

	public String ruleVersion() {
		return ruleVersion;
	}

}
