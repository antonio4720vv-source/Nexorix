package com.nexorix.notification;

import com.nexorix.bank.Bank;

import java.math.BigDecimal;

/** Lo que Nexorix entendio de una notificacion bancaria. balance puede ser null. */
public record ParsedNotification(Bank bank, String type, BigDecimal amount, String description, BigDecimal balance) {
}
