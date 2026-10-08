package com.nexorix.ai;

import com.nexorix.importer.ParsedMovement;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiParsingTest {

    private JsonNode json(String text) {
        return JsonMapper.builder().build().readTree(text);
    }

    @Test
    void convierteLosMovimientosLeidosPorLaIa() {
        AiStatementReader.AiReading reading = AiStatementReader.parse(json("""
                {"movements":[
                  {"date":"2026-03-31","description":"COMPRA EN EXITO","amount":43000.5,"type":"EGRESO"},
                  {"date":"2026-03-30","description":"PAGO NOMINA","amount":"1.500.000","type":"ingreso"}
                 ],
                 "totalCredits": 1500000, "totalDebits": null}
                """));

        List<ParsedMovement> rows = reading.movements();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(rows.get(0).amount()).isEqualByComparingTo("43000.50");
        assertThat(rows.get(0).warning()).contains("IA");
        assertThat(rows.get(1).type()).isEqualTo("INGRESO");
        assertThat(rows.get(1).amount()).isEqualByComparingTo("1500000");
        assertThat(reading.totalCredits()).isEqualByComparingTo("1500000");
        assertThat(reading.totalDebits()).isNull();
    }

    @Test
    void loQueLaIaNoLeyoBienQuedaComoErrorYNoSeInventa() {
        AiStatementReader.AiReading reading = AiStatementReader.parse(json("""
                {"movements":[
                  {"date":"fecha rara","description":"Algo","amount":100,"type":"EGRESO"},
                  {"date":"2026-03-01","description":"Sin tipo","amount":100,"type":"QUIZAS"},
                  {"date":"2026-03-01","description":"","amount":100,"type":"EGRESO"}
                ]}
                """));

        assertThat(reading.movements()).hasSize(3).noneMatch(ParsedMovement::isValid);
    }

    @Test
    void laClasificacionSoloAceptaCategoriasValidasYCoherentes() {
        List<AiClassifier.Item> asked = List.of(
                new AiClassifier.Item(0, "AWX JOY", "EGRESO"),
                new AiClassifier.Item(1, "PAGO EMPRESA", "INGRESO"),
                new AiClassifier.Item(2, "RARO", "EGRESO"),
                new AiClassifier.Item(3, "OTRO", "INGRESO"));

        Map<Integer, String> result = AiClassifier.parse(json("""
                {"items":[
                  {"i":0,"category":"entretenimiento"},
                  {"i":1,"category":"SALARIO"},
                  {"i":2,"category":"INVENTADA"},
                  {"i":3,"category":"MERCADO"},
                  {"i":9,"category":"MERCADO"}
                ]}
                """), asked);

        assertThat(result).containsEntry(0, "ENTRETENIMIENTO")
                .containsEntry(1, "SALARIO")
                .doesNotContainKeys(2, 3, 9);
    }
}
