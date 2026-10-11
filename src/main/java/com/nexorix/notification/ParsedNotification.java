package com.nexorix.notification;

import com.nexorix.bank.Bank;

import java.math.BigDecimal;

/**
 * Lo que Nexorix entendio de una notificacion bancaria.
 *
 * @param label    comercio o persona ("Exito", "Juan Perez"); null si el aviso no lo dice
 * @param purchase true si fue una compra o retiro (se paga con la cuenta), false si fue una transferencia
 * @param balance  saldo que informa el aviso, o null
 */
public record ParsedNotification(Bank bank, String type, BigDecimal amount, String label, boolean purchase,
                                 BigDecimal balance) {
}
