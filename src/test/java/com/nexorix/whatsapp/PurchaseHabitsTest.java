package com.nexorix.whatsapp;

import com.nexorix.fraud.AppNotification;
import com.nexorix.fraud.AppNotificationRepository;
import com.nexorix.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseHabitsTest {

    private final List<PurchaseNote> notes = new ArrayList<>();
    private final List<AppNotification> inbox = new ArrayList<>();
    private PurchaseHabits habits;
    private User ana;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        ana = new User("Ana", "ana", "ana@nexorix.com", "1020", "hash");
        ReflectionTestUtils.setField(ana, "id", 1L);
        PurchaseNoteRepository repository = mock(PurchaseNoteRepository.class);
        when(repository.findById(anyLong())).thenAnswer(call -> notes.stream()
                .filter(n -> n.getId().equals(call.getArgument(0))).findFirst());
        when(repository.findByUserIdOrderByPurchaseDateDescIdDesc(anyLong())).thenAnswer(call -> List.copyOf(notes));
        AppNotificationRepository notifications = mock(AppNotificationRepository.class);
        when(notifications.save(any(AppNotification.class))).thenAnswer(call -> {
            inbox.add(call.getArgument(0));
            return call.getArgument(0);
        });
        habits = new PurchaseHabits(repository, notifications, JsonMapper.builder().build());
    }

    private PurchaseNote answered(String category, String store) {
        PurchaseNote note = new PurchaseNote(ana, null, new BigDecimal("12000"), "Compra", LocalDateTime.now());
        ReflectionTestUtils.setField(note, "id", nextId++);
        note.answered("TEXTO", "x", store == null ? "{}" : "{\"tienda\":\"" + store + "\"}");
        note.setCategory(category);
        notes.add(note);
        return note;
    }

    private void seed() {
        for (int i = 0; i < 5; i++) {
            answered("Mercado", "Éxito");
        }
    }

    @Test
    void conPocoHistorialNoAvisaNada() {
        answered("Mercado", "Éxito");
        PurchaseNote today = answered("Cigarrillos", "Tienda Nueva");

        assertThat(habits.review(today.getId())).isEmpty();
        assertThat(inbox).isEmpty();
    }

    @Test
    void unaCategoriaNuncaUsadaGeneraAviso() {
        seed();
        PurchaseNote today = answered("Cigarrillos", "Éxito");

        assertThat(habits.review(today.getId())).hasSize(1).first().asString().contains("Cigarrillos");
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).getSeverity()).isEqualTo(AppNotification.Severity.WARNING);
    }

    @Test
    void unaTiendaNuevaGeneraAvisoAunqueLaCategoriaSeaHabitual() {
        seed();
        PurchaseNote today = answered("mercado", "Joyería Diamante");

        assertThat(habits.review(today.getId())).hasSize(1).first().asString().contains("Joyería Diamante");
    }

    @Test
    void unaCompraHabitualNoAvisa() {
        seed();
        PurchaseNote today = answered("Mercado", "éxito");

        assertThat(habits.review(today.getId())).isEmpty();
    }
}
