package com.nexorix.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationParserTest {

    @Test
    void entiendeUnIngresoDeNequi() {
        ParsedNotification n = NotificationParser.parse("com.nequi.MobileApp", "Nequi",
                "Recibiste $50.000 de Juan Perez").orElseThrow();

        assertThat(n.bank().name()).isEqualTo("Nequi");
        assertThat(n.type()).isEqualTo("INGRESO");
        assertThat(n.amount()).isEqualByComparingTo("50000");
        assertThat(n.label()).isEqualTo("Juan Perez");
        assertThat(n.purchase()).isFalse();
    }

    @Test
    void entiendeUnaCompraYElSaldo() {
        ParsedNotification n = NotificationParser.parse("com.app", "Bancolombia",
                "Bancolombia le informa Compra por $25.500,00 en EXITO CHAPINERO con tarjeta *1234. Saldo disponible: $1.200.000")
                .orElseThrow();

        assertThat(n.bank().name()).isEqualTo("Bancolombia");
        assertThat(n.type()).isEqualTo("EGRESO");
        assertThat(n.amount()).isEqualByComparingTo("25500");
        assertThat(n.balance()).isEqualByComparingTo("1200000");
        assertThat(n.label()).startsWith("EXITO CHAPINERO");
        assertThat(n.purchase()).isTrue();
    }

    @Test
    void entiendeUnEnvio() {
        ParsedNotification n = NotificationParser.parse("", "Daviplata", "Enviaste $1.000.000 a Maria Gomez")
                .orElseThrow();

        assertThat(n.bank().name()).isEqualTo("Daviplata");
        assertThat(n.type()).isEqualTo("EGRESO");
        assertThat(n.purchase()).isFalse();
        assertThat(n.label()).isEqualTo("Maria Gomez");
        assertThat(n.amount()).isEqualByComparingTo("1000000");
    }

    @Test
    void ignoraPromocionesClavesYRechazos() {
        assertThat(NotificationParser.parse("", "Nequi", "Gana $100.000 con tu primera compra")).isEmpty();
        assertThat(NotificationParser.parse("", "Bancolombia", "Tu código es 123456")).isEmpty();
        assertThat(NotificationParser.parse("", "Davivienda", "Compra por $20.000 rechazada")).isEmpty();
    }

    @Test
    void ignoraLoQueNoEsDeUnBancoOnoTieneMonto() {
        assertThat(NotificationParser.parse("com.whatsapp", "Ana", "Recibiste $50.000 de mi parte")).isEmpty();
        assertThat(NotificationParser.parse("", "Nequi", "Recibiste un regalo")).isEmpty();
    }
}
