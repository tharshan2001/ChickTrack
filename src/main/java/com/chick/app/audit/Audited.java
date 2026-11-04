package com.chick.app.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Records an {@code audit_logs} row when the annotated service method completes successfully. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

	/** Action name, for example {@code DISTRIBUTION_DISPATCH}. */
	String action();

	/** Entity type, for example {@code Distribution}. */
	String entityType();
}
