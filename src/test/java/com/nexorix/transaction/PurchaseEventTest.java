package com.nexorix.transaction;

import com.nexorix.account.Account;
import com.nexorix.account.AccountRepository;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseEventTest {

    private final List<Object> events = new ArrayList<>();
    private TransactionService service;

    @BeforeEach
    void setUp() {
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        service = new TransactionService(transactionRepository, accountRepository, userRepository, events::add);

        User ana = new User("Ana", "ana", "ana@nexorix.com", "1", "hash");
        ReflectionTestUtils.setField(ana, "id", 1L);
        Account nequi = new Account("Nequi Ana", "BILLETERA", "Nequi", new BigDecimal("3000000"), ana);
        Account davivienda = new Account("Davivienda Ana", "AHORROS", "Davivienda", BigDecimal.ZERO, ana);
        ReflectionTestUtils.setField(nequi, "id", 6L);
        ReflectionTestUtils.setField(davivienda, "id", 7L);

        when(userRepository.findByUsername("ana")).thenReturn(Optional.of(ana));
        when(accountRepository.findById(6L)).thenReturn(Optional.of(nequi));
        when(accountRepository.findById(7L)).thenReturn(Optional.of(davivienda));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(call -> {
            Transaction saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 50L);
            return saved;
        });
    }

    @Test
    void unEgresoAvisaQueHuboUnaCompra() {
        service.createTransaction(new BigDecimal("25000"), "EGRESO", "Exito Calle 80",
                LocalDateTime.now(), 6L, null, "ana");

        assertThat(events).hasSize(1);
        PurchaseRegisteredEvent event = (PurchaseRegisteredEvent) events.get(0);
        assertThat(event.transactionId()).isEqualTo(50L);
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.amount()).isEqualByComparingTo("25000");
        assertThat(event.description()).isEqualTo("Exito Calle 80");
    }

    @Test
    void unIngresoNoPreguntaNada() {
        service.createTransaction(BigDecimal.TEN, "INGRESO", "Salario", LocalDateTime.now(), 6L, null, "ana");
        assertThat(events).isEmpty();
    }
}
