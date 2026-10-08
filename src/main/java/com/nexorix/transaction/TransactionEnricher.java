package com.nexorix.transaction;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cuando la persona cuenta en que gasto (WhatsApp), completa la descripcion del
 * movimiento bancario: "Exito" pasa a "Exito - arroz y leche". Solo toca los
 * movimientos que vinieron del banco; los escritos a mano no se modifican.
 */
@Component
public class TransactionEnricher {

    private final TransactionRepository repository;

    public TransactionEnricher(TransactionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void enrich(Long transactionId, String detail) {
        if (transactionId == null || detail == null || detail.isBlank()) {
            return;
        }
        repository.findById(transactionId)
                .filter(transaction -> "BANK".equals(transaction.getSource()))
                .ifPresent(transaction -> {
                    String enriched = transaction.getDescription() + " - " + detail.trim();
                    transaction.setDescription(enriched.length() > 255 ? enriched.substring(0, 255) : enriched);
                });
    }
}
