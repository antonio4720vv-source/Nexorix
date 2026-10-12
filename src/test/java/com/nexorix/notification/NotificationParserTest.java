package com.nexorix.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationParserTest {

    @Test
    void entiendeUnIngresoDeNequi() {
        ParsedNotification n = NotificationParser.parse("com.nequi.MobileApp", "", "Nequi",
                "Recibiste $50.000 de Juan Perez").orElseThrow();

        assertThat(n.bank().name()).isEqualTo("Nequi");
        assertThat(n.type()).isEqualTo("INGRESO");
        assertThat(n.amount()).isEqualByComparingTo("50000");
        assertThat(n.label()).isEqualTo("Juan Perez");
        assertThat(n.purchase()).isFalse();
    }

    @Test
    void entiendeUnaCompraYElSaldo() {
        ParsedNotification n = NotificationParser.parse("com.app", "", "Bancolombia",
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
        ParsedNotification n = NotificationParser.parse("", "", "Daviplata", "Enviaste $1.000.000 a Maria Gomez")
                .orElseThrow();

        assertThat(n.bank().name()).isEqualTo("Daviplata");
        assertThat(n.type()).isEqualTo("EGRESO");
        assertThat(n.purchase()).isFalse();
        assertThat(n.label()).isEqualTo("Maria Gomez");
        assertThat(n.amount()).isEqualByComparingTo("1000000");
    }

    @Test
    void elNombreDeLaAppIdentificaElBancoAunqueElTextoNoLoDiga() {
        ParsedNotification n = NotificationParser.parse("com.x.y", "Bancolombia", "Aviso",
                "Compra por $25.000 en EXITO").orElseThrow();
        assertThat(n.bank().name()).isEqualTo("Bancolombia");

        ParsedNotification nequi = NotificationParser.parse("com.nequi.MobileApp", "Nequi", "Tienes plata",
                "Recibiste $10.000 de Ana").orElseThrow();
        assertThat(nequi.bank().name()).isEqualTo("Nequi");
        assertThat(NotificationParser.parse("com.whatsapp", "WhatsApp", "Ana", "Recibiste $10.000 de mi parte")).isEmpty();
    }

    @Test
    void ignoraPromocionesClavesYRechazos() {
        assertThat(NotificationParser.parse("", "", "Nequi", "Gana $100.000 con tu primera compra")).isEmpty();
        assertThat(NotificationParser.parse("", "", "Bancolombia", "Tu código es 123456")).isEmpty();
        assertThat(NotificationParser.parse("", "", "Davivienda", "Compra por $20.000 rechazada")).isEmpty();
    }

    @Test
    void ignoraLoQueNoEsDeUnBancoOnoTieneMonto() {
        assertThat(NotificationParser.parse("com.whatsapp", "", "Ana", "Recibiste $50.000 de mi parte")).isEmpty();
        assertThat(NotificationParser.parse("", "", "Nequi", "Recibiste un regalo")).isEmpty();
    }

    @Test
    void clasificaCadaAvisoSegunDeQueSeTrata() {
        assertThat(NotificationParser.analyze("com.whatsapp", "WhatsApp", "Ana", "hola").kind())
                .isEqualTo(NotificationParser.Kind.NOT_BANK);
        assertThat(NotificationParser.analyze("", "Nequi", "Nequi", "Tu código es 482910").kind())
                .isEqualTo(NotificationParser.Kind.SENSITIVE);
        assertThat(NotificationParser.analyze("", "Bancolombia", "Aviso", "Actualiza tu app hoy").kind())
                .isEqualTo(NotificationParser.Kind.INFO);
        assertThat(NotificationParser.analyze("", "Nequi", "Aviso", "Gana $100.000 con tu primera compra").kind())
                .isEqualTo(NotificationParser.Kind.INFO);
        assertThat(NotificationParser.analyze("", "Davivienda", "Aviso", "Operación por $50.000 en proceso").kind())
                .isEqualTo(NotificationParser.Kind.UNRECOGNIZED);
        assertThat(NotificationParser.analyze("", "Nequi", "Nequi", "Recibiste $5.000 de Ana").kind())
                .isEqualTo(NotificationParser.Kind.MOVEMENT);
    }
}
