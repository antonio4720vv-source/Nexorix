package com.nexorix.bank;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BankCatalogTest {

    @Test
    void encuentraElBancoSinImportarMayusculasNiTildes() {
        assertThat(BankCatalog.byName(" banco de bogota ")).get().extracting(Bank::name).isEqualTo("Banco de Bogotá");
        assertThat(BankCatalog.byName("NEQUI")).get().extracting(Bank::wallet).isEqualTo(true);
        assertThat(BankCatalog.byName("Banco Inventado")).isEmpty();
    }

    @Test
    void detectaElBancoDeUnTexto() {
        assertThat(BankCatalog.detect("com.nu.production", "Nu", "Pagaste $5.000").orElseThrow().name())
                .isEqualTo("Nu Colombia");
        assertThat(BankCatalog.detect("x", "Banco Popular informa", "Compra").orElseThrow().name())
                .isEqualTo("Banco Popular");
        assertThat(BankCatalog.detect("com.whatsapp", "Ana", "hola")).isEmpty();
    }
}
