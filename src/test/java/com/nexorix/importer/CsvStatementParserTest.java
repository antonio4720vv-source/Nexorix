package com.nexorix.importer;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvStatementParserTest {

    private final CsvStatementParser parser = new CsvStatementParser();

    private List<ParsedMovement> parse(String csv) {
        return parser.parse(csv.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void leeLaPlantillaDeNexorixConMontoConSigno() {
        List<ParsedMovement> rows = parse("""
                fecha,descripcion,valor,referencia
                2026-09-01,Salario octubre,3000000,NOM-10
                2026-09-02,"Compra Exito, Calle 80",-150000,
                """);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).type()).isEqualTo("INGRESO");
        assertThat(rows.get(0).reference()).isEqualTo("NOM-10");
        assertThat(rows.get(1).type()).isEqualTo("EGRESO");
        assertThat(rows.get(1).description()).isEqualTo("Compra Exito, Calle 80");
        assertThat(rows.get(1).amount()).isEqualByComparingTo("150000");
    }

    @Test
    void leeColumnasDeDebitoYCreditoConPuntoYComa() {
        List<ParsedMovement> rows = parse("""
                Extracto cuenta de ahorros
                Fecha;Descripción;Débito;Crédito;Saldo
                03/09/2026;PAGO PSE CLARO;89.900,00;;2.910.100,00
                04/09/2026;ABONO NOMINA;;3.000.000,00;5.910.100,00
                Total;;89.900,00;3.000.000,00;
                """);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(rows.get(0).type()).isEqualTo("EGRESO");
        assertThat(rows.get(0).amount()).isEqualByComparingTo("89900");
        assertThat(rows.get(1).type()).isEqualTo("INGRESO");
    }

    @Test
    void entiendeArchivosGuardadosConExcelEnWindows() {
        String csv = "Fecha;Descripción;Valor\n03/09/2026;Droguería;-25.000\n";
        List<ParsedMovement> rows = parser.parse(csv.getBytes(Charset.forName("windows-1252")));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).description()).isEqualTo("Droguería");
    }

    @Test
    void lasFilasConErroresSeMarcanSinDetenerLaLectura() {
        List<ParsedMovement> rows = parse("""
                fecha,descripcion,valor
                2026-09-01,Salario,3000000
                no es fecha,Algo,1000
                2026-09-03,Sin valor,
                """);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).isValid()).isTrue();
        assertThat(rows.get(1).error()).contains("fecha");
        assertThat(rows.get(2).error()).contains("valor");
    }

    @Test
    void sinEncabezadosConocidosDaUnErrorClaro() {
        assertThatThrownBy(() -> parse("a,b,c\n1,2,3\n"))
                .isInstanceOf(ImportException.class)
                .hasMessageContaining("columnas");
    }

    @Test
    void laColumnaDeTipoMandaSobreElSigno() {
        List<ParsedMovement> rows = parse("""
                fecha,descripcion,valor,tipo
                2026-09-01,Pago tarjeta,250000,Debito
                """);

        assertThat(rows.get(0).type()).isEqualTo("EGRESO");
    }
}
