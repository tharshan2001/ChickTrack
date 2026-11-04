package com.chick.app.audit;

import com.chick.app.identity.domain.UserAccount;
import com.chick.app.identity.repository.UserAccountRepository;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes an audit record after each successful {@link Audited} method. The record holds the
 * actor, the action, the entity id and the resulting state as JSON. A failure to write the audit
 * record is logged and never hides the business result.
 */
@Aspect
@Component
public class AuditAspect {

	private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

	private final AuditLogRepository auditLogs;
	private final UserAccountRepository users;
	private final JsonMapper json;

	public AuditAspect(AuditLogRepository auditLogs, UserAccountRepository users, JsonMapper json) {
		this.auditLogs = auditLogs;
		this.users = users;
		this.json = json;
	}

	@Around("@annotation(audited)")
	public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
		Object result = joinPoint.proceed();
		try {
			write(joinPoint, audited, result);
		}
		catch (RuntimeException ex) {
			log.error("Failed to write audit record {} for {}", audited.action(), audited.entityType(), ex);
		}
		return result;
	}

	private void write(ProceedingJoinPoint joinPoint, Audited audited, Object result) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String username = authentication == null ? null : authentication.getName();
		UUID actorId = username == null ? null : users.findByUsername(username).map(UserAccount::getId).orElse(null);

		Object[] args = joinPoint.getArgs();
		// The entity is the one addressed by id (dispatch, receive, reverse, update); otherwise the created one.
		String entityId = null;
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("method", ((MethodSignature) joinPoint.getSignature()).getMethod().getName());
		for (Object arg : args) {
			if (arg instanceof UUID uuid) {
				if (entityId == null) {
					entityId = uuid.toString();
				}
			}
			else if (arg != null && arg.getClass().isRecord()) {
				Object reason = component(arg, "reason");
				if (reason != null) {
					metadata.put("reason", reason);
				}
			}
		}

		if (entityId == null) {
			entityId = idOf(result);
		}
		auditLogs.append(actorId, username, audited.action(), audited.entityType(), entityId,
				result == null ? null : json.writeValueAsString(result),
				json.writeValueAsString(metadata));
	}

	private static String idOf(Object result) {
		Object id = result == null || !result.getClass().isRecord() ? null : component(result, "id");
		return id == null ? null : id.toString();
	}

	private static Object component(Object record, String name) {
		for (RecordComponent component : record.getClass().getRecordComponents()) {
			if (component.getName().equals(name)) {
				try {
					Method accessor = component.getAccessor();
					return accessor.invoke(record);
				}
				catch (ReflectiveOperationException ex) {
					return null;
				}
			}
		}
		return null;
	}
}
