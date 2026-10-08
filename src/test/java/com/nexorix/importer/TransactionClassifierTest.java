package com.nexorix.importer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionClassifierTest {

    @ParameterizedTest(name = "{0} ({1}) -> {2}")
    @CsvSource(delimiter = '|', value = {
            "COMPRA EXITO CALLE 80|EGRESO|MERCADO",
            "Tiendas D1 Chapinero|EGRESO|MERCADO",
            "RAPPI*RESTAURANTE|EGRESO|RESTAURANTES",
            "UBER TRIP|EGRESO|TRANSPORTE",
            "PAGO PSE CLARO|EGRESO|SERVICIOS",
            "Pago arriendo octubre|EGRESO|VIVIENDA",
            "DROGUERIA LA REBAJA|EGRESO|SALUD",
            "NETFLIX.COM|EGRESO|ENTRETENIMIENTO",
            "RETIRO CAJERO|EGRESO|RETIROS",
            "COMISION TRANSFERENCIA|EGRESO|BANCOS",
            "GMF 4X1000|EGRESO|BANCOS",
            "Transferencia a Nequi|EGRESO|TRANSFERENCIA",
            "PAGO NOMINA EMPRESA SAS|INGRESO|SALARIO",
            "Recibido de Juan|INGRESO|TRANSFERENCIA",
            "Intereses ahorro|INGRESO|OTROS_INGRESOS",
            "Algo raro|EGRESO|OTROS_GASTOS",
            "Mercado en Ara para la casa|EGRESO|MERCADO",
            "Enviaste a Antonio Martinez|EGRESO|TRANSFERENCIA",
            "Recibiste de Antonio Martinez|INGRESO|TRANSFERENCIA",
            "Impuesto del 4x1000|EGRESO|BANCOS",
            "GRAVAMEN AL MOVIMIENTO|EGRESO|BANCOS",
            "COMPRA EN BWIN LATAM SAS|EGRESO|ENTRETENIMIENTO",
            "Para WILDER FARID BARRAZA|EGRESO|TRANSFERENCIA",
            "TRANSFERENCIA DESDE NEQUI|INGRESO|TRANSFERENCIA"
    })
    void clasificaPorPalabras(String description, String type, String expected) {
        assertThat(TransactionClassifier.classify(description, type)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"Separar dinero|EGRESO|OTROS_GASTOS"})
    void noConfundePalabrasDentroDeOtras(String description, String type, String expected) {
        // "Separar" contiene "ara", pero no es el supermercado Ara.
        assertThat(TransactionClassifier.classify(description, type)).isEqualTo(expected);
    }
}
