package com.deepdimen.matrix.admin.api.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal request used by business modules to publish a user-facing message.
 *
 * <p>The publisher owns tenant resolution and recipient validation. Callers must
 * provide an explicit recipient list unless {@link #allUsers} is set.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessMessageRequest {

	private String bizKey;

	private String sourceType;

	private String sourceId;

	private String eventType;

	private String title;

	private String content;

	private String priority;

	private Boolean actionRequired;

	private Long orgId;

	private String targetPath;

	@Builder.Default
	private Map<String, String> targetQuery = new LinkedHashMap<>();

	private LocalDateTime expireTime;

	@Builder.Default
	private List<Long> recipientUserIds = new ArrayList<>();

	private boolean allUsers;

}
