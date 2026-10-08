package com.nexorix.importer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StatementValuesTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "1.500.000|1500000",
            "1.500.000,50|1500000.50",
            "1,500,000.50|1500000.50",
            "$ 89.900|89900",
            "-150.000,00|-150000.00",
            "(12.500)|-12500",
            "50.000-|-50000",
            "1.500|1500",
            "1500,5|1500.5",
            "COP 2.000|2000"
    })
    void leeMontosColombianos(String raw, String expected) {
        assertThat(StatementValues.parseAmount(raw)).isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    void textoSinNumerosNoEsUnMonto() {
        assertThat(StatementValues.parseAmount("valor")).isNull();
        assertThat(StatementValues.parseAmount("")).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "2026-10-03|2026-10-03",
            "03/10/2026|2026-10-03",
            "3-10-26|2026-10-03",
            "03.10.2026|2026-10-03",
            "03/10|2026-10-03",
            "03 oct 2026|2026-10-03",
            "3 OCT|2026-10-03",
            "03-Oct-2026|2026-10-03",
            "1 sept 2026|2026-09-01"
    })
    void leeFechasEnVariosFormatos(String raw, String expected) {
        assertThat(StatementValues.parseDate(raw, 2026)).isEqualTo(LocalDate.parse(expected));
    }

    @Test
    void rechazaFechasImposibles() {
        assertThat(StatementValues.parseDate("31/02/2026", 2026)).isNull();
        assertThat(StatementValues.parseDate("01/01/1990", 2026)).isNull();
        assertThat(StatementValues.parseDate("hola", 2026)).isNull();
    }

    @Test
    void limpiaLasDescripciones() {
        assertThat(StatementValues.cleanDescription("  COMPRA   EXITO\t<b>CALLE 80</b> - "))
                .isEqualTo("COMPRA EXITO bCALLE 80/b");
        assertThat(StatementValues.normalizeForMatching("Compra Éxito, Calle 80"))
                .isEqualTo("compra exito calle 80");
    }
}
