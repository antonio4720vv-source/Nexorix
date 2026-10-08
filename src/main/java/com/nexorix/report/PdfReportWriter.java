package com.nexorix.report;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Reporte conciliado en PDF, listo para entregar al contador:
 * resumen del dinero real, transferencias internas excluidas, categorias,
 * meses y el detalle de los ingresos reales (lo que mas importa para la DIAN).
 */
public final class PdfReportWriter {

    private static final float MARGIN = 50;
    private static final float WIDTH = PDRectangle.LETTER.getWidth();
    private static final float HEIGHT = PDRectangle.LETTER.getHeight();
    private static final float[] INK = {0.06f, 0.16f, 0.18f};
    private static final float[] MUTED = {0.39f, 0.47f, 0.48f};
    private static final float[] TEAL = {0.05f, 0.54f, 0.48f};
    private static final float[] LINE = {0.86f, 0.90f, 0.89f};
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PDDocument document = new PDDocument();
    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private PDPageContentStream content;
    private float y;
    private int pageNumber;

    private PdfReportWriter() {
    }

    public static byte[] write(ReportData data) {
        PdfReportWriter writer = new PdfReportWriter();
        try {
            return writer.render(data);
        } catch (IOException exception) {
            throw new IllegalStateException("No fue posible generar el PDF.", exception);
        }
    }

    private byte[] render(ReportData data) throws IOException {
        newPage();

        // ---------- Encabezado ----------
        text("NEXORIX", MARGIN, y, bold, 20, TEAL);
        text("Reporte de flujo real de dinero", MARGIN, y - 22, bold, 14, INK);
        text("Generado el " + LocalDate.now(ZoneId.of("America/Bogota")).format(DAY), WIDTH - MARGIN - 150, y, regular, 9, MUTED);
        y -= 44;
        text("Titular: " + data.personName() + "   ·   Documento: " + mask(data.documentNumber()), MARGIN, y, regular, 10, INK);
        y -= 14;
        text("Periodo: " + period(data.from(), data.to()), MARGIN, y, regular, 10, INK);
        y -= 22;

        // ---------- Resumen ----------
        heading("1. Resumen");
        var s = data.summary();
        summaryRow("Ingresos según los extractos", s.grossIncome(), false);
        summaryRow("(-) Transferencias entre cuentas propias (excluidas)", s.internalTransfers(), false);
        summaryRow("= Ingresos reales", s.realIncome(), true);
        summaryRow("Gastos según los extractos", s.grossExpense(), false);
        summaryRow("= Gastos reales (incluye comisiones)", s.realExpense(), true);
        summaryRow("   de los cuales, comisiones de transferencias", s.transferFees(), false);
        summaryRow("Flujo neto real", s.realNetFlow(), true);
        y -= 4;
        small("Transferencias internas confirmadas en Trace: " + s.confirmedTransfers()
                + ". Sugerencias sin confirmar: " + s.pendingSuggestions()
                + (s.pendingSuggestions() > 0 ? " (siguen contando como ingreso/gasto hasta confirmarlas)." : "."));
        y -= 10;

        // ---------- Categorias ----------
        heading("2. Ingresos reales por categoría");
        table(data.realIncomesByCategory());
        heading("3. Gastos reales por categoría");
        table(data.realExpensesByCategory());

        // ---------- Meses ----------
        heading("4. Por mes");
        tableHeader(new String[]{"Mes", "Ingresos reales", "Gastos reales", "Neto"}, new float[]{MARGIN, 230, 360, 480});
        for (ReportData.MonthRow m : data.months()) {
            ensure(16);
            text(m.month(), MARGIN, y, regular, 9, INK);
            right(money(m.realIncome()), 330, regular);
            right(money(m.realExpense()), 460, regular);
            right(money(m.realIncome().subtract(m.realExpense())), WIDTH - MARGIN, bold);
            y -= 14;
        }
        if (data.months().isEmpty()) small("Sin movimientos en el periodo.");
        y -= 10;

        // ---------- Detalle de ingresos reales ----------
        heading("5. Detalle de ingresos reales");
        tableHeader(new String[]{"Fecha", "Banco", "Descripción", "Valor real"}, new float[]{MARGIN, 115, 215, 480});
        int count = 0;
        for (ReportData.Row r : data.rows()) {
            if (!"INGRESO".equals(r.type()) || r.realPart().signum() <= 0) continue;
            ensure(16);
            text(r.date().format(DAY), MARGIN, y, regular, 8.5f, INK);
            text(cut(r.bank(), 18), 115, y, regular, 8.5f, INK);
            text(cut(r.description(), 52), 215, y, regular, 8.5f, INK);
            right(money(r.realPart()), WIDTH - MARGIN, regular);
            y -= 13;
            count++;
        }
        if (count == 0) small("No hay ingresos reales en el periodo.");

        y -= 16;
        ensure(60);
        small("Este reporte es informativo: separa las transferencias entre cuentas propias del dinero que realmente");
        small("entró o salió, según los extractos cargados y las conciliaciones confirmadas en Nexorix. No reemplaza");
        small("la asesoría de un contador ni constituye una declaración tributaria. El detalle completo está en el CSV.");

        content.close();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        document.close();
        return out.toByteArray();
    }

    // ============================================================
    // DIBUJO
    // ============================================================

    private void newPage() throws IOException {
        if (content != null) content.close();
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        content = new PDPageContentStream(document, page);
        pageNumber++;
        y = HEIGHT - MARGIN;
        text("Nexorix · Reporte de flujo real · página " + pageNumber, MARGIN, 28, regular, 8, MUTED);
    }

    private void ensure(float space) throws IOException {
        if (y - space < MARGIN) newPage();
    }

    private void heading(String title) throws IOException {
        ensure(40);
        text(title, MARGIN, y, bold, 12, TEAL);
        y -= 6;
        line(MARGIN, y, WIDTH - MARGIN, y);
        y -= 14;
    }

    private void summaryRow(String label, BigDecimal value, boolean strong) throws IOException {
        ensure(16);
        text(label, MARGIN, y, strong ? bold : regular, 10, INK);
        right(money(value), WIDTH - MARGIN, strong ? bold : regular);
        y -= 15;
    }

    private void table(Map<String, BigDecimal> values) throws IOException {
        BigDecimal total = values.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (values.isEmpty()) {
            small("Sin datos en el periodo.");
            y -= 6;
            return;
        }
        for (Map.Entry<String, BigDecimal> e : values.entrySet()) {
            ensure(16);
            text(e.getKey(), MARGIN, y, regular, 9.5f, INK);
            String percent = total.signum() == 0 ? "" : e.getValue().multiply(BigDecimal.valueOf(100))
                    .divide(total, 1, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " %";
            right(percent, 420, regular);
            right(money(e.getValue()), WIDTH - MARGIN, regular);
            y -= 14;
        }
        ensure(16);
        text("Total", MARGIN, y, bold, 9.5f, INK);
        right(money(total), WIDTH - MARGIN, bold);
        y -= 20;
    }

    private void tableHeader(String[] titles, float[] xs) throws IOException {
        ensure(24);
        for (int i = 0; i < titles.length; i++) {
            if (i == titles.length - 1) right(titles[i], WIDTH - MARGIN, bold);
            else text(titles[i], xs[i], y, bold, 9, MUTED);
        }
        y -= 5;
        line(MARGIN, y, WIDTH - MARGIN, y);
        y -= 12;
    }

    private void small(String value) throws IOException {
        ensure(14);
        text(value, MARGIN, y, regular, 8.5f, MUTED);
        y -= 12;
    }

    private void right(String value, float rightX, PDType1Font font) throws IOException {
        String safe = safe(value, font);
        float width = font.getStringWidth(safe) / 1000 * 9.5f;
        text(safe, rightX - width, y, font, 9.5f, INK);
    }

    private void text(String value, float x, float yy, PDType1Font font, float size, float[] color) throws IOException {
        content.beginText();
        content.setNonStrokingColor(color[0], color[1], color[2]);
        content.setFont(font, size);
        content.newLineAtOffset(x, yy);
        content.showText(safe(value, font));
        content.endText();
    }

    private void line(float x1, float y1, float x2, float y2) throws IOException {
        content.setStrokingColor(LINE[0], LINE[1], LINE[2]);
        content.setLineWidth(0.8f);
        content.moveTo(x1, y1);
        content.lineTo(x2, y2);
        content.stroke();
    }

    // ============================================================
    // TEXTO
    // ============================================================

    /** Las fuentes basicas del PDF solo tienen caracteres latinos: el resto se reemplaza. */
    static String safe(String value, PDType1Font font) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        for (char c : value.replace('−', '-').replace('→', '>').toCharArray()) {
            try {
                font.encode(String.valueOf(c));
                out.append(c);
            } catch (IllegalArgumentException | IOException exception) {
                out.append('?');
            }
        }
        return out.toString();
    }

    static String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.of("es", "CO"));
        format.setMaximumFractionDigits(0);
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        return (v.signum() < 0 ? "-$ " : "$ ") + format.format(v.abs());
    }

    private static String cut(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    static String mask(String document) {
        if (document == null || document.length() < 4) return "****";
        return "****" + document.substring(document.length() - 4);
    }

    private static String period(LocalDate from, LocalDate to) {
        if (from == null && to == null) return "Todo el historial";
        return (from == null ? "inicio" : from.format(DAY)) + " a " + (to == null ? "hoy" : to.format(DAY));
    }
}
