package com.deepdimen.matrix.admin.mobilepush;

import com.deepdimen.matrix.common.core.util.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mobile/push")
public class MobilePushController {

	private final MobilePushServiceImpl service;

	private final PushSessionGuard sessions;

	public MobilePushController(MobilePushServiceImpl service, PushSessionGuard sessions) {
		this.service = service;
		this.sessions = sessions;
	}

	@PostMapping("/installations")
	public R<MobilePushServiceImpl.BindingDTO> register(@Valid @RequestBody PushRegistrationDTO request,
			@RequestHeader(value = "X-Installation-Credential", required = false) String credential) {
		service.requireEnabled();
		return R.ok(service.register(sessions.current(), request, credential));
	}

	@PatchMapping("/installations/current")
	public R<MobilePushServiceImpl.BindingDTO> synchronize(@Valid @RequestBody PushRegistrationDTO request,
			@RequestHeader("X-Installation-Credential") String credential) {
		service.requireEnabled();
		return R.ok(service.register(sessions.current(), request, credential));
	}

	@DeleteMapping("/installations/current/subscription")
	public R<Boolean> unsubscribe(@RequestHeader("X-Installation-Id") String installationId,
			@RequestHeader(value = "X-Installation-Credential", required = false) String credential,
			@RequestHeader("X-Installation-Generation") long generation) {
		service.requireEnabled();
		return R.ok(service.unsubscribe(sessions.current(), installationId, credential, generation));
	}

	@GetMapping("/preferences")
	public R<MobilePushServiceImpl.SettingsDTO> preferences() {
		return R.ok(service.settings(sessions.current()));
	}

	@PutMapping("/preferences")
	public R<PushPreferencesDTO> preferences(@RequestBody PushPreferencesDTO preferences) {
		service.requireEnabled();
		return R.ok(service.savePreferences(sessions.current(), preferences));
	}

}
