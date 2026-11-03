package com.chick.app.report.export;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PdfReportExporter {

	private static final Font TITLE_FONT = new Font(Font.BOLD, 14);
	private static final Font HEADER_FONT = new Font(Font.BOLD, 9);
	private static final Font BODY_FONT = new Font(Font.NORMAL, 8);
	private static final Font TOTAL_FONT = new Font(Font.BOLD, 9);

	public byte[] export(ReportTable table) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4.rotate(), 36, 36, 42, 36);
		try {
			PdfWriter.getInstance(document, output);
			document.open();
			document.add(new Paragraph(humanize(table.name()) + " Report", TITLE_FONT));
			document.add(new Paragraph(" ", BODY_FONT));

			PdfPTable pdfTable = new PdfPTable(Math.max(table.headers().size(), 1));
			pdfTable.setWidthPercentage(100);
			for (String header : table.headers()) {
				pdfTable.addCell(new Phrase(header, HEADER_FONT));
			}
			for (List<String> row : table.rows()) {
				for (String value : row) {
					pdfTable.addCell(new Phrase(value, BODY_FONT));
				}
			}
			pdfTable.setHeaderRows(1);
			document.add(pdfTable);

			if (!table.totals().isEmpty()) {
				document.add(new Paragraph(" ", BODY_FONT));
				document.add(new Paragraph("Totals", TOTAL_FONT));
				for (var entry : table.totals().entrySet()) {
					document.add(new Paragraph(entry.getKey() + ": " + entry.getValue(), TOTAL_FONT));
				}
			}
			document.close();
		}
		catch (DocumentException ex) {
			throw new com.chick.app.common.exception.BusinessException(
					"REPORT_GENERATION_FAILED",
					"Unable to generate PDF report",
					org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
		}
		return output.toByteArray();
	}

	private String humanize(String name) {
		String spaced = name.replace('_', ' ');
		return spaced.substring(0, 1).toUpperCase() + spaced.substring(1);
	}
}
