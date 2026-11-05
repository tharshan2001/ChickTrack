package com.chick.app.report.controller;

import com.chick.app.common.api.ApiResponse;
import com.chick.app.report.dto.DashboardResponse;
import com.chick.app.report.dto.DistributionReportRow;
import com.chick.app.report.dto.ProcurementReportRow;
import com.chick.app.report.dto.ProfitLossResponse;
import com.chick.app.report.dto.ProfitReportRow;
import com.chick.app.report.dto.ReportResult;
import com.chick.app.report.dto.SalesReportRow;
import com.chick.app.report.dto.SettlementReportRow;
import com.chick.app.report.dto.ShrinkageReportRow;
import com.chick.app.report.dto.StockReportRow;
import com.chick.app.report.export.CsvReportExporter;
import com.chick.app.report.export.ExportFormat;
import com.chick.app.report.export.PdfReportExporter;
import com.chick.app.report.export.ReportTable;
import com.chick.app.report.service.DashboardService;
import com.chick.app.report.service.ProfitLossService;
import com.chick.app.report.service.ReportExportService;
import com.chick.app.report.service.ReportService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReportController {

	private static final String REPORT_ACCESS =
			"hasAnyRole('OWNER','ACCOUNTANT','VIEWER','PROCUREMENT_MANAGER','WAREHOUSE_MANAGER','DISTRIBUTION_MANAGER')";

	private final DashboardService dashboardService;
	private final ReportService reportService;
	private static final String FINANCE_ACCESS = "hasAnyRole('OWNER','ACCOUNTANT')";

	private final ReportExportService exportService;
	private final ProfitLossService profitLossService;
	private final CsvReportExporter csvExporter;
	private final PdfReportExporter pdfExporter;

	public ReportController(
			DashboardService dashboardService,
			ReportService reportService,
			ReportExportService exportService,
			ProfitLossService profitLossService,
			CsvReportExporter csvExporter,
			PdfReportExporter pdfExporter) {
		this.dashboardService = dashboardService;
		this.reportService = reportService;
		this.exportService = exportService;
		this.profitLossService = profitLossService;
		this.csvExporter = csvExporter;
		this.pdfExporter = pdfExporter;
	}

	/**
	 * Profit and loss. {@code period}: daily, monthly (default), yearly or custom. {@code date} picks
	 * the day, month or year (default today); {@code month=2026-11} and {@code year=2026} pick one
	 * directly; custom needs {@code from} and {@code to}. {@code groupBy}: none, day, month, year.
	 */
	@GetMapping("/reports/profit-loss")
	@PreAuthorize(FINANCE_ACCESS)
	public ResponseEntity<ApiResponse<ProfitLossResponse>> profitLoss(
			@RequestParam(required = false) String period,
			@RequestParam(required = false) LocalDate date,
			@RequestParam(required = false) String month,
			@RequestParam(required = false) Integer year,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) String groupBy,
			@RequestParam(required = false) UUID branchId) {
		ProfitLossService.Range range = profitLossService.resolve(period, date, month, year, from, to, groupBy);
		return ResponseEntity.ok(ApiResponse.of(profitLossService.profitLoss(range, branchId)));
	}

	@GetMapping("/reports/profit-loss/export")
	@PreAuthorize(FINANCE_ACCESS)
	public ResponseEntity<byte[]> exportProfitLoss(
			@RequestParam(required = false, defaultValue = "csv") String format,
			@RequestParam(required = false) String period,
			@RequestParam(required = false) LocalDate date,
			@RequestParam(required = false) String month,
			@RequestParam(required = false) Integer year,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) String groupBy,
			@RequestParam(required = false) UUID branchId) {
		ExportFormat exportFormat = ExportFormat.parse(format);
		ProfitLossService.Range range = profitLossService.resolve(period, date, month, year, from, to, groupBy);
		ReportTable table = profitLossService.toTable(profitLossService.profitLoss(range, branchId));
		byte[] content = exportFormat == ExportFormat.PDF ? pdfExporter.export(table) : csvExporter.export(table);
		String filename = "profit-loss-" + range.from() + "-to-" + range.to() + "." + exportFormat.extension();
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_TYPE, exportFormat.contentType())
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
				.body(content);
	}

	@GetMapping("/dashboard")
	@PreAuthorize(REPORT_ACCESS)
	public ResponseEntity<ApiResponse<DashboardResponse>> dashboard(
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) UUID branchId) {
		return ResponseEntity.ok(ApiResponse.of(dashboardService.dashboard(from, to, branchId)));
	}

	@GetMapping("/reports/procurement")
	@PreAuthorize(REPORT_ACCESS)
	public ResponseEntity<ApiResponse<ReportResult<ProcurementReportRow>>> procurement(
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) UUID supplierId) {
		return ResponseEntity.ok(ApiResponse.of(reportService.procurement(from, to, supplierId)));
	}

	@GetMapping("/reports/stock")
	@PreAuthorize(REPORT_ACCESS)
	public ResponseEntity<ApiResponse<ReportResult<StockReportRow>>> stock(
			@RequestParam(required = false) UUID chickenTypeId,
			@RequestParam(required = false) LocalDate asOf) {
		return ResponseEntity.ok(ApiResponse.of(reportService.stock(chickenTypeId, asOf)));
	}

	@GetMapping("/reports/distribution")
	@PreAuthorize(REPORT_ACCESS)
	public ResponseEntity<ApiResponse<ReportResult<DistributionReportRow>>> distribution(
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) UUID branchId) {
		return ResponseEntity.ok(ApiResponse.of(reportService.distribution(from, to, branchId)));
	}

	@GetMapping("/reports/profit")
	@PreAuthorize(REPORT_ACCESS)
	public ResponseEntity<ApiResponse<ReportResult<ProfitReportRow>>> profit(
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) UUID branchId) {
		return ResponseEntity.ok(ApiResponse.of(reportService.profit(from, to, branchId)));
	}

	@GetMapping("/reports/sales")
	@PreAuthorize(REPORT_ACCESS)
	public ResponseEntity<ApiResponse<ReportResult<SalesReportRow>>> sales(
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) UUID branchId) {
		return ResponseEntity.ok(ApiResponse.of(reportService.sales(from, to, branchId)));
	}

	@GetMapping("/reports/shrinkage")
	@PreAuthorize(REPORT_ACCESS)
	public ResponseEntity<ApiResponse<ReportResult<ShrinkageReportRow>>> shrinkage(
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) UUID branchId) {
		return ResponseEntity.ok(ApiResponse.of(reportService.shrinkage(from, to, branchId)));
	}

	@GetMapping("/reports/settlements")
	@PreAuthorize(REPORT_ACCESS)
	public ResponseEntity<ApiResponse<ReportResult<SettlementReportRow>>> settlements(
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) UUID branchId) {
		return ResponseEntity.ok(ApiResponse.of(reportService.settlements(from, to, branchId)));
	}

	@GetMapping("/reports/{name}/export")
	@PreAuthorize(REPORT_ACCESS)
	public ResponseEntity<byte[]> export(
			@PathVariable String name,
			@RequestParam(required = false, defaultValue = "csv") String format,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) UUID branchId,
			@RequestParam(required = false) UUID supplierId,
			@RequestParam(required = false) UUID chickenTypeId) {
		ReportExportService.ExportedFile file = exportService.export(
				name,
				ExportFormat.parse(format),
				from,
				to,
				branchId,
				supplierId,
				chickenTypeId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_TYPE, file.contentType())
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
				.body(file.content());
	}
}
