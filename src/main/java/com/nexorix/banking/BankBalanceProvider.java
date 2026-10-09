package com.nexorix.banking;

import java.math.BigDecimal;

/**
 * De donde sale el saldo de una cuenta recien vinculada. La persona nunca lo escribe:
 * en produccion lo consulta al agregador bancario (Plaid / Prometeo / Belvo) con la autorizacion
 * que acaba de dar; en la demo lo inventa {@link DemoBankBalanceProvider}.
 */
public interface BankBalanceProvider {

    BigDecimal currentBalance(String bank, String externalRef);
}
