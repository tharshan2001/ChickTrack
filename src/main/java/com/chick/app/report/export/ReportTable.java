package com.chick.app.report.export;

import java.util.List;
import java.util.Map;

public record ReportTable(
		String name,
		List<String> headers,
		List<List<String>> rows,
		Map<String, String> totals) {

	public ReportTable {
		headers = List.copyOf(headers);
		rows = rows.stream().map(List::copyOf).toList();
	}
}
