package com.nexorix.fraud;

/** Hay que avisar YA por WhatsApp y SMS (se publica al guardar la alerta; se envia despues del commit). */
public record CriticalFraudAlertEvent(Long userId, Long notificationId, String smsText, String whatsappText) {
}
