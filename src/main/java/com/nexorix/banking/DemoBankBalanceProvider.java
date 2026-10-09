package com.nexorix.banking;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Saldo de demostracion: estable para la misma cuenta (sale de la referencia), entre $150.000 y $4.000.000. */
@Component
public class DemoBankBalanceProvider implements BankBalanceProvider {

    @Override
    public BigDecimal currentBalance(String bank, String externalRef) {
        long seed = (bank + "|" + externalRef).hashCode() & 0x7fffffffL;
        long thousands = 150 + seed % 3_851;
        return BigDecimal.valueOf(thousands * 1_000L);
    }
}
