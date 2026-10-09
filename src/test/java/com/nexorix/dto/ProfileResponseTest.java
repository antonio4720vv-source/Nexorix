package com.nexorix.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileResponseTest {

    @Test
    void censuraNombreCedulaTelefonoYCorreo() {
        assertEquals("Ant**** Jos*", ProfileResponse.maskName("Antonio José"));
        assertEquals("10******02", ProfileResponse.maskKeepEnds("1012345602", 2, 2));
        assertEquals("+53313*****", ProfileResponse.maskPhone("+5331312345"));
        assertEquals("+53313*****", ProfileResponse.maskPhone("5331312345"));
        assertEquals("an*@gmail.com", ProfileResponse.maskEmail("ana@gmail.com"));
        assertEquals("NX-1A2B3C4D", ProfileResponse.accountNumber("1a2b3c4d-0000-1111-2222-333344445555"));
    }

    @Test
    void valoresVaciosNoFallan() {
        assertEquals("", ProfileResponse.maskName(null));
        assertEquals("", ProfileResponse.maskPhone(null));
        assertEquals("", ProfileResponse.maskEmail("sin-arroba"));
        assertEquals("***", ProfileResponse.maskKeepEnds("123", 2, 2));
    }
}
