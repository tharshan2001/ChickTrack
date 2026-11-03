package com.chick.app.report.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

@Component
public class CsvReportExporter {

	public byte[] export(ReportTable table) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		CSVFormat format = CSVFormat.DEFAULT.builder()
				.setHeader(table.headers().toArray(String[]::new))
				.build();
		try (CSVPrinter printer = new CSVPrinter(new java.io.OutputStreamWriter(output, StandardCharsets.UTF_8), format)) {
			for (List<String> row : table.rows()) {
				printer.printRecord(row);
			}
			if (!table.totals().isEmpty()) {
				printer.println();
				for (var entry : table.totals().entrySet()) {
					printer.printRecord(entry.getKey(), entry.getValue());
				}
			}
		}
		catch (IOException ex) {
			throw new com.chick.app.common.exception.BusinessException(
					"REPORT_GENERATION_FAILED",
					"Unable to generate CSV report",
					org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
		}
		return output.toByteArray();
	}
}
