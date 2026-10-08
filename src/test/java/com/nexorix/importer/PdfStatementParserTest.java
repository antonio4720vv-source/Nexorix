package com.nexorix.importer;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfStatementParserTest {

    private final PdfStatementParser parser = new PdfStatementParser();

    private static final String EXTRACTO = """
            BANCO DE PRUEBA S.A.          EXTRACTO DE CUENTA DE AHORROS
            Periodo: 01/09/2026 al 30/09/2026
            FECHA   DESCRIPCION                          VALOR          SALDO
            Saldo anterior                                               3.000.000,00
            03/09   COMPRA EXITO CALLE 80                150.000,00     2.850.000,00
            04/09   PAGO NOMINA EMPRESA SAS            3.000.000,00     5.850.000,00
            05/09   TRANSFERENCIA A NEQUI              1.000.000,00     4.850.000,00
            06/09   CUOTA DE MANEJO                       (12.500)      4.837.500,00
            Pagina 1 de 1
            """;

    @Test
    void encuentraLosMovimientosYDescartaElRestoDelTexto() {
        List<ParsedMovement> rows = parser.parseText(EXTRACTO);

        assertThat(rows).hasSize(4);
        assertThat(rows.get(0).description()).isEqualTo("COMPRA EXITO CALLE 80");
        assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2026, 9, 3));
    }

    @Test
    void usaElSaldoParaSaberSiEntroOSalioDinero() {
        List<ParsedMovement> rows = parser.parseText(EXTRACTO);

        assertThat(rows.get(0).type()).isEqualTo("EGRESO");   // 3.000.000 - 150.000 = 2.850.000
        assertThat(rows.get(1).type()).isEqualTo("INGRESO");  // 2.850.000 + 3.000.000 = 5.850.000
        assertThat(rows.get(2).type()).isEqualTo("EGRESO");
        assertThat(rows.get(3).type()).isEqualTo("EGRESO");   // parentesis
        assertThat(rows.get(1).amount()).isEqualByComparingTo("3000000");
    }

    @Test
    void sinSaldoUsaLasPalabras() {
        List<ParsedMovement> rows = parser.parseText("""
                01/09/2026 ABONO TRANSFERENCIA RECIBIDA 200.000,00
                02/09/2026 COMPRA RAPPI 45.000,00
                """);

        assertThat(rows.get(0).type()).isEqualTo("INGRESO");
        assertThat(rows.get(1).type()).isEqualTo("EGRESO");
    }

    @Test
    void siNoSabeElTipoLoAvisa() {
        List<ParsedMovement> rows = parser.parseText("03/09/2026 MOVIMIENTO XYZ 10.000,00\n");

        assertThat(rows.get(0).warning()).contains("Revísalo");
    }

    @Test
    void usaElAnoDelExtractoParaFechasSinAno() {
        assertThat(PdfStatementParser.mostCommonYear("Periodo 2025 ... 2025 ... 2024")).isEqualTo(2025);
    }

    @Test
    void unArchivoQueNoEsPdfDaUnErrorClaro() {
        assertThatThrownBy(() -> parser.parse("no soy un pdf".getBytes(), null))
                .isInstanceOf(ImportException.class);
    }

    // ------------------------------------------------------------
    // Formato de Nu: fecha sin ano, valores con + y -, y el 4x1000
    // en una linea aparte SIN fecha.
    // ------------------------------------------------------------

    private static final String NU = """
            Extracto de tu cuenta Nu - agosto 2026
            Resumen del periodo
            Total recibido                                  +$23.400,00
            01 ago   Recibiste de Ana Prueba                +$20.000,00
            02 ago   Enviaste a Ana Prueba                  -$39.800,00
                     Impuesto del 4x1000                       -$159,20
            16 ago   Recibiste de Ana Prueba                 +$3.400,00
            Total del mes                                   -$16.559,20
            """;

    @Test
    void leeElFormatoDeNuConSignosYCobrosSinFecha() {
        List<ParsedMovement> rows = parser.parseText(NU);

        assertThat(rows).hasSize(4);

        assertThat(rows.get(0).type()).isEqualTo("INGRESO");
        assertThat(rows.get(0).amount()).isEqualByComparingTo("20000");
        assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2026, 8, 1));

        assertThat(rows.get(1).type()).isEqualTo("EGRESO");
        assertThat(rows.get(1).amount()).isEqualByComparingTo("39800");

        // El 4x1000 toma la fecha del movimiento anterior.
        assertThat(rows.get(2).description()).isEqualTo("Impuesto del 4x1000");
        assertThat(rows.get(2).date()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(rows.get(2).amount()).isEqualByComparingTo("159.20");
        assertThat(rows.get(2).type()).isEqualTo("EGRESO");

        assertThat(rows.get(3).type()).isEqualTo("INGRESO");
    }

    @Test
    void lasLineasDeTotalesNoSonMovimientos() {
        List<ParsedMovement> rows = parser.parseText(NU);

        assertThat(rows).noneMatch(row -> row.description().toLowerCase().contains("total"));
    }

    // ------------------------------------------------------------
    // Formato de Bancolombia: lo mas reciente primero, salidas con
    // "$-" y entradas SIN signo, montos con coma de miles y punto decimal.
    // ------------------------------------------------------------

    private static final String BANCOLOMBIA = """
            Estado de deposito de bajo monto para el periodo de: 2026/03/01 a 2026/03/31
            Saldo anterior $988,456.85 Saldo promedio $190,870.41
            Total abonos $5,766,270.25 Cuentas por cobrar $0.00
            Fecha del movimiento Descripcion Valor Saldo
            31/03/2026 GRAVAMEN AL MOVIMIENTO $-172.00 $1,073.35
            31/03/2026 COMPRA EN BWIN LATAM SAS $-43,000.00 $1,245.35
            30/03/2026 PAGO DE PROV EMPRESA SAS $40,000.00 $44,245.35
            30/03/2026 Para WILDER FARID BARRAZA $-7,000.00 $4,245.35
            29/03/2026 TRANSFERENCIA DESDE NEQUI $1,245.35 $11,245.35
            """;

    @Test
    void leeElFormatoDeBancolombiaEnOrdenInverso() {
        List<ParsedMovement> rows = parser.parseText(BANCOLOMBIA);

        assertThat(rows).hasSize(5);

        assertThat(rows.get(0).type()).isEqualTo("EGRESO");
        assertThat(rows.get(0).amount()).isEqualByComparingTo("172.00");
        assertThat(rows.get(1).amount()).isEqualByComparingTo("43000.00");

        // "PAGO DE PROV" dice "pago", pero el saldo muestra que ENTRO dinero:
        // 4,245.35 + 40,000.00 = 44,245.35
        assertThat(rows.get(2).type()).isEqualTo("INGRESO");
        assertThat(rows.get(2).amount()).isEqualByComparingTo("40000.00");

        assertThat(rows.get(3).type()).isEqualTo("EGRESO");
        assertThat(rows.get(4).type()).isEqualTo("INGRESO");
        assertThat(rows.get(4).date()).isEqualTo(LocalDate.of(2026, 3, 29));
    }

    @Test
    void lasLineasDeResumenDelBancoNoSonMovimientos() {
        List<ParsedMovement> rows = parser.parseText(BANCOLOMBIA);

        assertThat(rows).noneMatch(row -> row.description().toLowerCase().contains("abonos"));
    }

    @Test
    void encuentraLosTotalesDelResumenDelBanco() {
        PdfStatementParser.Totals totals = PdfStatementParser.findTotals(BANCOLOMBIA
                + "\nTotal cargos $6,753,653.75 Valor de intereses pagados $93.25\n");

        assertThat(totals.credits()).isEqualByComparingTo("5766270.25");
        assertThat(totals.debits()).isEqualByComparingTo("6753653.75");
    }

    @Test
    void sinResumenNoHayTotales() {
        assertThat(PdfStatementParser.findTotals("03/09/2026 COMPRA 1.000,00").isEmpty()).isTrue();
    }
}
