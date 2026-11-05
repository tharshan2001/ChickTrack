package com.chick.app.identity.dto;

import com.chick.app.audit.AuditView;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * The user plus the password the owner hands over. {@code temporaryPassword} is present only when
 * the server generated it, and is returned once: it is never stored in clear or audited.
 */
public record UserCredentialsResponse(
		@JsonUnwrapped UserResponse user,
		@JsonInclude(JsonInclude.Include.NON_NULL) String temporaryPassword) implements AuditView {

	@Override
	public Object auditView() {
		return user;
	}
}
