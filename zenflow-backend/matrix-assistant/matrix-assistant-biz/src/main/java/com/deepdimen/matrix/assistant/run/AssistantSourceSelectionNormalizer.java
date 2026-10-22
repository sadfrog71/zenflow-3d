package com.deepdimen.matrix.assistant.run;

import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Component;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.SourceMode;

/** Central compatibility boundary for legacy and explicit source-selection modes. */
@Component
public class AssistantSourceSelectionNormalizer {

	public NormalizedSourceSelection normalize(SourceMode requestedMode, List<ProviderId> requestedProviderIds,
			List<ProviderId> sessionActiveProviderIds) {
		SourceMode sourceMode = SourceMode.effective(requestedMode);
		List<ProviderId> requested = requestedProviderIds == null ? null
				: List.copyOf(new LinkedHashSet<>(requestedProviderIds));
		List<ProviderId> active = sessionActiveProviderIds == null ? List.of()
				: List.copyOf(new LinkedHashSet<>(sessionActiveProviderIds));
		return switch (sourceMode) {
			case LEGACY -> new NormalizedSourceSelection(sourceMode, requested == null ? active : requested,
					requested != null);
			case AUTO -> {
				if (requested != null && !requested.isEmpty()) {
					throw conflict();
				}
				// U1 keeps the P1 execution shape. U4 replaces this compatibility seed with
				// the frozen tenant-enabled Provider catalog before any call is made.
				yield new NormalizedSourceSelection(sourceMode, active, false);
			}
			case CAP -> {
				if (requested == null || requested.isEmpty()) {
					throw conflict();
				}
				yield new NormalizedSourceSelection(sourceMode, requested, true);
			}
			case NONE -> {
				if (requested != null && !requested.isEmpty()) {
					throw conflict();
				}
				yield new NormalizedSourceSelection(sourceMode, List.of(), true);
			}
		};
	}

	private IllegalArgumentException conflict() {
		return new IllegalArgumentException("SOURCE_MODE_CONFLICT");
	}

	public record NormalizedSourceSelection(SourceMode sourceMode, List<ProviderId> providerIds,
			boolean providerSelectionExplicit) {

		public NormalizedSourceSelection {
			providerIds = providerIds == null ? List.of() : List.copyOf(providerIds);
		}

	}

}
