package com.nexorix.transaction;

import com.nexorix.account.Account;
import com.nexorix.account.AccountRepository;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionServiceTest {

    private TransactionService service;
    private Account nequi;
    private Account davivienda;
    private Account ajena;

    @BeforeEach
    void setUp() {
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        service = new TransactionService(transactionRepository, accountRepository, userRepository);

        User ana = new User("Ana", "ana", "ana@nexorix.com", "1", "hash");
        ReflectionTestUtils.setField(ana, "id", 1L);
        User otra = new User("Otra", "otra", "otra@nexorix.com", "2", "hash");
        ReflectionTestUtils.setField(otra, "id", 2L);

        nequi = new Account("Nequi Ana", "BILLETERA", "Nequi", new BigDecimal("3000000"), ana);
        davivienda = new Account("Davivienda Ana", "AHORROS", "Davivienda", BigDecimal.ZERO, ana);
        ajena = new Account("Cuenta de otra", "AHORROS", "Nu", BigDecimal.TEN, otra);
        ReflectionTestUtils.setField(nequi, "id", 6L);
        ReflectionTestUtils.setField(davivienda, "id", 7L);
        ReflectionTestUtils.setField(ajena, "id", 9L);

        when(userRepository.findByUsername("ana")).thenReturn(Optional.of(ana));
        when(accountRepository.findById(6L)).thenReturn(Optional.of(nequi));
        when(accountRepository.findById(7L)).thenReturn(Optional.of(davivienda));
        when(accountRepository.findById(9L)).thenReturn(Optional.of(ajena));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void laDescripcionEsObligatoria() {
        assertThatThrownBy(() -> service.createTransaction(BigDecimal.TEN, "INGRESO", "  ",
                LocalDateTime.now(), 6L, null, "ana"))
                .hasMessageContaining("descripción");
    }

    @Test
    void noAceptaMontosNegativos() {
        assertThatThrownBy(() -> service.createTransaction(new BigDecimal("-5"), "INGRESO", "x",
                LocalDateTime.now(), 6L, null, "ana"))
                .hasMessageContaining("mayor que cero");
    }
}
