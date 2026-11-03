package com.chick.app.report.export;

import java.util.Locale;

public enum ExportFormat {

	CSV("text/csv", "csv"),
	PDF("application/pdf", "pdf");

	private final String contentType;
	private final String extension;

	ExportFormat(String contentType, String extension) {
		this.contentType = contentType;
		this.extension = extension;
	}

	public String contentType() {
		return contentType;
	}

	public String extension() {
		return extension;
	}

	public static ExportFormat parse(String value) {
		if (value == null || value.isBlank()) {
			return CSV;
		}
		try {
			return ExportFormat.valueOf(value.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new com.chick.app.common.exception.BusinessException(
					"VALIDATION_FAILED",
					"Unsupported export format '" + value + "'. Supported formats: csv, pdf",
					org.springframework.http.HttpStatus.BAD_REQUEST);
		}
	}
}
