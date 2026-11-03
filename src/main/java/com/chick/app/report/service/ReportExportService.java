package com.chick.app.report.service;

import com.chick.app.common.exception.BusinessException;
import com.chick.app.report.dto.DistributionReportRow;
import com.chick.app.report.dto.ProcurementReportRow;
import com.chick.app.report.dto.ProfitReportRow;
import com.chick.app.report.dto.SettlementReportRow;
import com.chick.app.report.dto.ShrinkageReportRow;
import com.chick.app.report.dto.StockReportRow;
import com.chick.app.report.export.CsvReportExporter;
import com.chick.app.report.export.ExportFormat;
import com.chick.app.report.export.PdfReportExporter;
import com.chick.app.report.export.ReportTable;
import com.chick.app.report.export.ReportTableFactory;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportExportService {

	private final ReportService reports;
	private final ReportTableFactory tables;
	private final CsvReportExporter csvExporter;
	private final PdfReportExporter pdfExporter;

	public ReportExportService(
			ReportService reports,
			ReportTableFactory tables,
			CsvReportExporter csvExporter,
			PdfReportExporter pdfExporter) {
		this.reports = reports;
		this.tables = tables;
		this.csvExporter = csvExporter;
		this.pdfExporter = pdfExporter;
	}

	@Transactional(readOnly = true)
	public ExportedFile export(
			String name,
			ExportFormat format,
			LocalDate from,
			LocalDate to,
			UUID branchId,
			UUID supplierId,
			UUID chickenTypeId) {
		ReportTable table = table(name, from, to, branchId, supplierId, chickenTypeId);
		byte[] content = format == ExportFormat.PDF ? pdfExporter.export(table) : csvExporter.export(table);
		return new ExportedFile(content, format.contentType(), filename(name, format, from, to));
	}

	private ReportTable table(
			String name,
			LocalDate from,
			LocalDate to,
			UUID branchId,
			UUID supplierId,
			UUID chickenTypeId) {
		return switch (name.toLowerCase(Locale.ROOT)) {
			case "procurement" -> tables.toTable(reports.procurement(from, to, supplierId), ProcurementReportRow.class);
			case "stock" -> tables.toTable(reports.stock(chickenTypeId, to), StockReportRow.class);
			case "distribution" -> tables.toTable(reports.distribution(from, to, branchId), DistributionReportRow.class);
			case "profit" -> tables.toTable(reports.profit(from, to, branchId), ProfitReportRow.class);
			case "shrinkage" -> tables.toTable(reports.shrinkage(from, to, branchId), ShrinkageReportRow.class);
			case "settlements" -> tables.toTable(reports.settlements(from, to, branchId), SettlementReportRow.class);
			default -> throw new BusinessException("REPORT_NOT_FOUND",
					"Unknown report '" + name + "'. Available reports: procurement, stock, distribution, profit, shrinkage, settlements",
					HttpStatus.NOT_FOUND);
		};
	}

	private String filename(String name, ExportFormat format, LocalDate from, LocalDate to) {
		StringBuilder builder = new StringBuilder(name.toLowerCase(Locale.ROOT));
		if (from != null) {
			builder.append('-').append(from);
		}
		if (to != null) {
			builder.append("-to-").append(to);
		}
		builder.append('.').append(format.extension());
		return builder.toString();
	}

	public record ExportedFile(byte[] content, String contentType, String filename) {
	}
}
