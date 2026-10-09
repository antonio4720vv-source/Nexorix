package com.nexorix.whatsapp;

import com.nexorix.fraud.AppNotification;
import com.nexorix.fraud.AppNotification.Severity;
import com.nexorix.fraud.AppNotificationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * La IA va conociendo como compra la persona: cuando una compra respondida trae una categoria o una
 * tienda que NUNCA habia usado (y ya hay historial suficiente), deja un aviso en la app. El aviso
 * tambien sale como notificacion del dispositivo (PushDispatcher).
 */
@Component
public class PurchaseHabits {

    /** Compras respondidas que hacen falta para saber que es "habitual". */
    static final int MIN_HISTORY = 5;

    private static final TypeReference<Map<String, String>> MAP = new TypeReference<>() {
    };

    private final PurchaseNoteRepository notes;
    private final AppNotificationRepository notifications;
    private final ObjectMapper mapper;

    public PurchaseHabits(PurchaseNoteRepository notes, AppNotificationRepository notifications, ObjectMapper mapper) {
        this.notes = notes;
        this.notifications = notifications;
        this.mapper = mapper;
    }

    /** Revisa una compra recien respondida. Devuelve los avisos que dejo (para pruebas). */
    @Transactional
    public List<String> review(Long noteId) {
        PurchaseNote note = notes.findById(noteId).orElse(null);
        if (note == null) {
            return List.of();
        }
        Long userId = note.getUser().getId();
        List<PurchaseNote> before = notes.findByUserIdOrderByPurchaseDateDescIdDesc(userId).stream()
                .filter(other -> !other.getId().equals(note.getId()) && other.getStatus() == PurchaseNoteStatus.RESPONDIDA)
                .toList();
        if (before.size() < MIN_HISTORY) {
            return List.of();
        }

        Set<String> categories = new HashSet<>();
        Set<String> stores = new HashSet<>();
        for (PurchaseNote other : before) {
            if (other.getCategory() != null) {
                categories.add(key(other.getCategory()));
            }
            String store = store(other);
            if (store != null) {
                stores.add(key(store));
            }
        }

        java.util.ArrayList<String> alerts = new java.util.ArrayList<>();
        String amount = PurchaseQuestionNotifier.money(note.getAmount());
        if (note.getCategory() != null && !categories.contains(key(note.getCategory()))) {
            alerts.add("Compra poco habitual: es la primera vez que registras algo en «" + note.getCategory()
                    + "» (" + amount + " en " + note.getDescription() + ").");
        }
        String store = store(note);
        if (store != null && !stores.contains(key(store))) {
            alerts.add("Compraste en una tienda que no sueles usar: " + store + " (" + amount + ").");
        }
        for (String alert : alerts) {
            notifications.save(new AppNotification(note.getUser(), Severity.WARNING, "Compra inusual", alert, null));
        }
        return alerts;
    }

    private String store(PurchaseNote note) {
        if (note.getValuesJson() == null || note.getValuesJson().isBlank()) {
            return null;
        }
        try {
            String value = mapper.readValue(note.getValuesJson(), MAP).get("tienda");
            return value == null || value.isBlank() ? null : value.trim();
        } catch (Exception exception) {
            return null;
        }
    }

    private static String key(String text) {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
