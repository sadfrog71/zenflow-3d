package com.deepdimen.matrix.admin.mobilepush;

public record PushPreferencesDTO(boolean enabled, boolean taskEnabled, boolean informationEnabled,
		boolean systemEnabled) {
	public static PushPreferencesDTO defaults() {
		return new PushPreferencesDTO(false, true, false, false);
	}

	public boolean allows(String source) {
		if (!enabled)
			return false;
		return "TASK".equals(source) ? taskEnabled : "INFORMATION".equals(source) ? informationEnabled : systemEnabled;
	}
}
