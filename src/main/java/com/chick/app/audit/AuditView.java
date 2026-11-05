package com.chick.app.audit;

/**
 * A result that must not be audited as returned (it carries a secret, for example). The audit
 * record stores {@link #auditView()} instead.
 */
public interface AuditView {

	Object auditView();
}
