package com.chick.app.report.export;

import com.chick.app.report.dto.ReportResult;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReportTableFactory {

	public <T> ReportTable toTable(ReportResult<T> result, Class<T> rowType) {
		List<RecordComponent> components = Arrays.asList(rowType.getRecordComponents());
		List<String> headers = components.stream()
				.map(component -> humanize(component.getName()))
				.toList();

		List<List<String>> rows = new ArrayList<>();
		for (T row : result.rows()) {
			List<String> values = new ArrayList<>(components.size());
			for (RecordComponent component : components) {
				values.add(format(read(component, row)));
			}
			rows.add(values);
		}

		Map<String, String> totals = new LinkedHashMap<>();
		result.totals().forEach((key, value) -> totals.put(humanize(key), value.toPlainString()));

		return new ReportTable(result.name(), headers, rows, totals);
	}

	private Object read(RecordComponent component, Object row) {
		try {
			return component.getAccessor().invoke(row);
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Unable to read report column " + component.getName(), ex);
		}
	}

	private String format(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof BigDecimal decimal) {
			return decimal.toPlainString();
		}
		if (value instanceof Enum<?> enumValue) {
			return enumValue.name();
		}
		if (value instanceof UUID || value instanceof Temporal) {
			return value.toString();
		}
		return String.valueOf(value);
	}

	private String humanize(String name) {
		String spaced = name.replace('_', ' ');
		return spaced.substring(0, 1).toUpperCase() + spaced.substring(1);
	}
}
