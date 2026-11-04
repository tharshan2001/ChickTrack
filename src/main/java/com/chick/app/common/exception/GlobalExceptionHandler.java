package com.chick.app.common.exception;

import com.chick.app.common.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return response("VALIDATION_FAILED", "Request validation failed", HttpStatus.BAD_REQUEST, fieldErrors, request);
	}

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
		return response(ex.getCode(), ex.getMessage(), ex.getStatus(), null, request);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
		return response("RESOURCE_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND, null, request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		return response("FORBIDDEN", "You do not have permission for this action", HttpStatus.FORBIDDEN, null, request);
	}

	@ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
	ResponseEntity<ApiError> handleConcurrentChange(RuntimeException ex, HttpServletRequest request) {
		log.info("Concurrent modification: {}", ex.getMessage());
		return response("CONCURRENT_MODIFICATION",
				"The record was changed by another request; reload and try again", HttpStatus.CONFLICT, null, request);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
		log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
		return response("DATA_CONFLICT",
				"The request conflicts with existing data", HttpStatus.CONFLICT, null, request);
	}

	@ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
	ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest request) {
		return response("VALIDATION_FAILED", "Malformed request", HttpStatus.BAD_REQUEST, null, request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
		log.error("Unhandled request failure", ex);
		return response("INTERNAL_ERROR", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR, null, request);
	}

	private ResponseEntity<ApiError> response(
			String code,
			String message,
			HttpStatus status,
			Map<String, String> fieldErrors,
			HttpServletRequest request) {
		ApiError error = new ApiError(code, message, fieldErrors, request.getHeader("X-Request-Id"), Instant.now());
		return ResponseEntity.status(status).body(error);
	}
}
