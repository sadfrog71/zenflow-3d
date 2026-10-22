package com.deepdimen.matrix.admin.mobilepush;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PushRegistrationDTO {

	@NotBlank
	@Pattern(regexp = "[0-9a-fA-F-]{36}")
	private String installationId;

	@Min(1)
	private long generation;

	@NotBlank
	@Pattern(regexp = "ANDROID")
	private String platform;

	@NotBlank
	@Pattern(regexp = "HMS_ANDROID")
	private String channel;

	@NotBlank
	@Size(max = 128)
	private String appId;

	@NotBlank
	@Size(max = 4096)
	private String token;

	@NotBlank
	@Pattern(regexp = "GRANTED|DENIED|UNKNOWN|UNAVAILABLE")
	private String permissionState;

	@NotBlank
	@Size(max = 64)
	private String appVersion;

	@NotBlank
	@Size(max = 64)
	private String osVersion;

	@Override
	public String toString() {
		return "PushRegistrationDTO[redacted]";
	}

}
