package com.chick.app.report.dto;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ReportResult<T>(String name, List<T> rows, Map<String, BigDecimal> totals) {

	public ReportResult {
		rows = List.copyOf(rows);
		totals = Collections.unmodifiableMap(new LinkedHashMap<>(totals));
	}
}
