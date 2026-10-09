package com.nexorix.split;

import com.nexorix.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplitPaymentMatcherTest {

    private static SplitShare share(String name, String cedula) {
        User u = new User(name, name.toLowerCase().replace(" ", "."), name + "@x.co", cedula, "x");
        return new SplitShare(null, u, new BigDecimal("10000"), "t-" + name);
    }

    @Test
    void sinIdentidadSoloSeAsignaSiHayUnSoloCobroConEseMonto() {
        SplitShare juan = share("Juan Pérez", "1111111");
        assertEquals(juan, SplitPaymentMatcher.pick(List.of(juan), null, null).orElseThrow());
        assertTrue(SplitPaymentMatcher.pick(List.of(juan, share("Luis Mora", "2222222")), null, null).isEmpty());
    }

    @Test
    void conIdentidadSeEligeAQuienPago() {
        SplitShare juan = share("Juan Pérez", "1111111");
        SplitShare luis = share("Luis Mora", "2222222");
        assertEquals(luis, SplitPaymentMatcher.pick(List.of(juan, luis), "LUIS A MORA", null).orElseThrow());
        assertEquals(juan, SplitPaymentMatcher.pick(List.of(juan, luis), null, "1.111.111").orElseThrow());
    }

    @Test
    void otraPersonaConElMismoMontoNoSaldaElCobro() {
        assertTrue(SplitPaymentMatcher.pick(List.of(share("Juan Pérez", "1111111")), "Carlos Gómez", null).isEmpty());
    }
}
