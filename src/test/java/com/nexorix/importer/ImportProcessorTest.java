package com.nexorix.importer;

import com.nexorix.ai.AiClassifier;
import com.nexorix.ai.AiStatementReader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportProcessorTest {

    private AiStatementReader aiReader;
    private ImportProcessor processor;

    @BeforeEach
    void setUp() {
        aiReader = mock(AiStatementReader.class);
        processor = new ImportProcessor(mock(ImportBatchRepository.class), mock(ImportRowRepository.class),
                mock(ImportService.class), aiReader, mock(AiClassifier.class),
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    /** Crea un PDF real (con texto) para las pruebas. */
    static byte[] pdf(String... lines) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                content.newLineAtOffset(40, 740);
                for (String line : lines) {
                    content.showText(line);
                    content.newLineAtOffset(0, -16);
                }
                content.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static final String[] EXTRACTO_QUE_CUADRA = {
            "Estado de cuenta 2026",
            "Total abonos $3,000,000.00",
            "Total cargos $150,000.00",
            "01/09/2026 PAGO NOMINA EMPRESA $3,000,000.00 $3,000,000.00",
            "02/09/2026 COMPRA EN EXITO $-150,000.00 $2,850,000.00"
    };

    @Test
    void unPdfQueCuadraConLosTotalesNoNecesitaIa() throws Exception {
        when(aiReader.isEnabled()).thenReturn(true);

        ImportProcessor.Analysis analysis = processor.analyze("PDF", pdf(EXTRACTO_QUE_CUADRA), null);

        assertThat(analysis.aiUsed()).isFalse();
        assertThat(analysis.movements()).hasSize(2);
        assertThat(analysis.checkMessage()).startsWith("Cuadra");
        verify(aiReader, never()).read(any());
    }

    @Test
    void siNoCuadraYLaIaSiCuadraSeUsaLaLecturaDeLaIa() throws Exception {
        when(aiReader.isEnabled()).thenReturn(true);
        // Al extracto le "falta" un movimiento para las reglas: la IA lo encuentra.
        byte[] file = pdf("Estado de cuenta 2026", "Total abonos $3,000,000.00", "Total cargos $200,000.00",
                "01/09/2026 PAGO NOMINA EMPRESA $3,000,000.00 $3,000,000.00",
                "02/09/2026 COMPRA EN EXITO $-150,000.00 $2,850,000.00",
                "Movimiento en dos lineas que las reglas no entienden");

        when(aiReader.read(any())).thenReturn(new AiStatementReader.AiReading(List.of(
                ParsedMovement.ok(1, LocalDate.of(2026, 9, 1), "PAGO NOMINA", new BigDecimal("3000000"), "INGRESO", null, null),
                ParsedMovement.ok(2, LocalDate.of(2026, 9, 2), "COMPRA EN EXITO", new BigDecimal("150000"), "EGRESO", null, null),
                ParsedMovement.ok(3, LocalDate.of(2026, 9, 3), "CUOTA DE MANEJO", new BigDecimal("50000"), "EGRESO", null, null)
        ), null, null));

        ImportProcessor.Analysis analysis = processor.analyze("PDF", file, null);

        assertThat(analysis.aiUsed()).isTrue();
        assertThat(analysis.movements()).hasSize(3);
        assertThat(analysis.checkMessage()).startsWith("Cuadra");
    }

    @Test
    void sinIaUnPdfQueNoCuadraIgualSeMuestraConElAviso() throws Exception {
        when(aiReader.isEnabled()).thenReturn(false);
        byte[] file = pdf("Total abonos $9,999,999.00",
                "01/09/2026 PAGO NOMINA EMPRESA $3,000,000.00 $3,000,000.00");

        ImportProcessor.Analysis analysis = processor.analyze("PDF", file, null);

        assertThat(analysis.aiUsed()).isFalse();
        assertThat(analysis.checkMessage()).startsWith("No cuadra");
    }

    @Test
    void unPdfSinMovimientosYSinIaDaUnErrorClaro() throws Exception {
        when(aiReader.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> processor.analyze("PDF", pdf("Hola, esto no es un extracto bancario de nada"), null))
                .isInstanceOf(ImportException.class);
    }

    @Test
    void elCsvSeLeeSinIa() {
        byte[] csv = "fecha;descripcion;valor\n2026-09-01;Salario;3000000\n".getBytes(StandardCharsets.UTF_8);

        ImportProcessor.Analysis analysis = processor.analyze("CSV", csv, null);

        assertThat(analysis.movements()).hasSize(1);
        assertThat(analysis.aiUsed()).isFalse();
    }

    @Test
    void reglasParaPedirAyudaALaIa() {
        ParsedMovement ok = ParsedMovement.ok(1, LocalDate.of(2026, 9, 1), "x", BigDecimal.TEN, "EGRESO", null, null);
        ParsedMovement dudoso = ParsedMovement.ok(2, LocalDate.of(2026, 9, 1), "y", BigDecimal.TEN, "EGRESO", null, "Revísalo");
        PdfStatementParser.Totals sinTotales = new PdfStatementParser.Totals(null, null);

        assertThat(ImportProcessor.needsAi(List.of(), sinTotales, false)).isTrue();
        assertThat(ImportProcessor.needsAi(List.of(ok, ok, ok), sinTotales, false)).isFalse();
        assertThat(ImportProcessor.needsAi(List.of(ok, dudoso), sinTotales, false)).isTrue();
        assertThat(ImportProcessor.needsAi(List.of(ok),
                new PdfStatementParser.Totals(null, new BigDecimal("99")), false)).isTrue();
    }
}
