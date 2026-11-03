package com.chick.app.report.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.chick.app.report.dto.ProcurementReportRow;
import com.chick.app.report.dto.ReportResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportExportTest {

	private final ReportTableFactory tableFactory = new ReportTableFactory();
	private final CsvReportExporter csvExporter = new CsvReportExporter();

	@Test
	void buildsTableFromRecordRows() {
		ReportResult<ProcurementReportRow> result = new ReportResult<>(
				"procurement",
				List.of(new ProcurementReportRow(
						"PRC-2026-000001",
						LocalDate.of(2026, 1, 15),
						"Test Supplier",
						1,
						new BigDecimal("1000.000"),
						new BigDecimal("830000.00"),
						new BigDecimal("16600.00"),
						new BigDecimal("9000.00"),
						new BigDecimal("855600.00"),
						new BigDecimal("855.6000"))),
				Map.of("totalCost", new BigDecimal("855600.00")));

		ReportTable table = tableFactory.toTable(result, ProcurementReportRow.class);

		assertThat(table.name()).isEqualTo("procurement");
		assertThat(table.headers()).containsExactly(
				"Reference",
				"ProcurementDate",
				"SupplierName",
				"ItemCount",
				"PaidWeightKg",
				"PurchaseValue",
				"CommissionAmount",
				"ExpenseAmount",
				"TotalCost",
				"LandedCostPerKg");
		assertThat(table.rows()).hasSize(1);
		assertThat(table.rows().get(0)).containsExactly(
				"PRC-2026-000001",
				"2026-01-15",
				"Test Supplier",
				"1",
				"1000.000",
				"830000.00",
				"16600.00",
				"9000.00",
				"855600.00",
				"855.6000");
		assertThat(table.totals()).containsEntry("TotalCost", "855600.00");
	}

	@Test
	void exportsCsvWithHeadersRowsAndTotals() {
		Map<String, String> totals = new LinkedHashMap<>();
		totals.put("TotalCost", "855600.00");
		ReportTable table = new ReportTable(
				"procurement",
				List.of("Reference", "TotalCost"),
				List.of(List.of("PRC-2026-000001", "855600.00")),
				totals);

		String csv = new String(csvExporter.export(table), StandardCharsets.UTF_8);

		assertThat(csv).contains("Reference,TotalCost");
		assertThat(csv).contains("PRC-2026-000001,855600.00");
		assertThat(csv).contains("TotalCost,855600.00");
	}
}
