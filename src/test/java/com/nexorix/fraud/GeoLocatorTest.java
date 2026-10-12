package com.nexorix.fraud;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeoLocatorTest {

    private final GeoLocator locator = new GeoLocator();

    @Test
    void encuentraLaCiudadDentroDelTextoDeUnAviso() {
        assertThat(locator.findInText("Compra por $50.000 en EXITO CHAPINERO BOGOTA con tarjeta *1234").orElseThrow().city())
                .isEqualTo("Bogotá");
        Geo miami = locator.findInText("Compra en BEST BUY MIAMI FL").orElseThrow();
        assertThat(miami.city()).isEqualTo("Miami");
        assertThat(miami.country()).isEqualTo("US");
    }

    @Test
    void ganaElNombreMasLargo() {
        assertThat(locator.findInText("Retiro en cajero SANTA MARTA").orElseThrow().city()).isEqualTo("Santa Marta");
    }

    @Test
    void noInventaCiudadesConPalabrasComunes() {
        assertThat(locator.findInText("Compra en PIZZERIA ROMA")).isEmpty();
        assertThat(locator.findInText("Compra en TIENDA LIMA Y LIMON")).isEmpty();
        assertThat(locator.findInText("Pago en PASTO Y JARDIN")).isEmpty();
        assertThat(locator.findInText("Compra en SUPERMERCADO")).isEmpty();
    }

    @Test
    void bogotaAMiamiEnUnaHoraEsImposibleYBogotaAMedellinNo() {
        Geo bogota = locator.resolve("Bogotá", null, null, null);
        double km = bogota.distanceKm(locator.resolve("Miami", null, null, null));
        assertThat(km).isBetween(2300.0, 2700.0);
        assertThat(bogota.distanceKm(locator.resolve("Medellín", null, null, null))).isBetween(200.0, 280.0);
    }

    @Test
    void laCiudadSinPaisToma_elPaisDelCatalogo() {
        assertThat(locator.resolve("Cancún", null, null, null).country()).isEqualTo("MX");
        assertThat(locator.resolve("Ciudad Inventada", null, null, null)).isNull();
    }
}
