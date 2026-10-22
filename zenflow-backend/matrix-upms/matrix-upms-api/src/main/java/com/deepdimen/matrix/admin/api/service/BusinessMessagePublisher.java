package com.deepdimen.matrix.admin.api.service;

import com.deepdimen.matrix.admin.api.dto.BusinessMessageRequest;

/** Publishes business events through the framework's existing user message center. */
public interface BusinessMessagePublisher {

	/**
	 * Publishes one idempotent message and returns the existing or newly created id.
	 * @param request normalized business message request
	 * @return message id
	 */
	Long publish(BusinessMessageRequest request);

}
