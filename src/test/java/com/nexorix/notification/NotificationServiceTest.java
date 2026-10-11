package com.nexorix.notification;

import com.nexorix.account.Account;
import com.nexorix.account.AccountRepository;
import com.nexorix.account.AccountService;
import com.nexorix.transaction.TransactionRepository;
import com.nexorix.transaction.TransactionService;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private NotificationTokenRepository tokens;
    private AccountRepository accounts;
    private AccountService accountService;
    private TransactionService transactions;
    private TransactionRepository transactionRepository;
    private NotificationService service;
    private User ana;
    private NotificationToken stored;

    @BeforeEach
    void setUp() {
        tokens = mock(NotificationTokenRepository.class);
        UserRepository users = mock(UserRepository.class);
        accounts = mock(AccountRepository.class);
        accountService = mock(AccountService.class);
        transactions = mock(TransactionService.class);
        transactionRepository = mock(TransactionRepository.class);
        service = new NotificationService(tokens, users, accounts, accountService, transactions, transactionRepository);

        ana = new User("Ana", "ana", "ana@nexorix.com", "1", "hash");
        ReflectionTestUtils.setField(ana, "id", 1L);
        when(users.findByUsername("ana")).thenReturn(Optional.of(ana));
        when(tokens.findByUserUsername("ana")).thenReturn(Optional.empty());
        when(accounts.findByUserUsername("ana")).thenReturn(List.of());
        when(tokens.save(any(NotificationToken.class))).thenAnswer(call -> {
            stored = call.getArgument(0);
            return stored;
        });
    }

    private String newToken() {
        String token = service.createToken("ana");
        when(tokens.findByTokenHash(NotificationService.hash(token))).thenReturn(Optional.of(stored));
        return token;
    }

    @Test
    void laLlaveSeGuardaSoloComoHuella() {
        String token = newToken();

        assertThat(token).startsWith("nxr_");
        assertThat(ReflectionTestUtils.getField(stored, "tokenHash")).isEqualTo(NotificationService.hash(token))
                .isNotEqualTo(token);
    }

    @Test
    void rechazaUnaLlaveQueNoExiste() {
        assertThatThrownBy(() -> service.ingest("nxr_falsa", "", "Nequi", "Recibiste $5.000 de Ana", null))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.ingest(null, "", "Nequi", "Recibiste $5.000 de Ana", null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void creaLaCuentaDelBancoSiNoExisteYRegistraElMovimiento() {
        String token = newToken();
        Account created = new Account("Nequi", "BILLETERA", "Nequi", BigDecimal.ZERO, ana);
        when(accountService.createAccount("Nequi", "BILLETERA", "Nequi", BigDecimal.ZERO, "ana")).thenReturn(created);

        var outcome = service.ingest(token, "com.nequi.MobileApp", "Nequi", "Recibiste $50.000 de Juan Perez", null);

        assertThat(outcome).isEqualTo(NotificationService.Outcome.REGISTERED);
        verify(transactions).saveFromNotification(eq(created), eq(new BigDecimal("50000")), eq("INGRESO"),
                eq("Recibido de Juan Perez"), any(), anyString(), eq(null));
        assertThat(stored.getReceived()).isEqualTo(1);
    }

    @Test
    void noRegistraDosVecesElMismoAviso() {
        String token = newToken();
        when(transactionRepository.existsByAccountUserIdAndReference(eq(1L), anyString())).thenReturn(true);

        var outcome = service.ingest(token, "", "Nequi", "Recibiste $50.000 de Juan Perez", 1760000000000L);

        assertThat(outcome).isEqualTo(NotificationService.Outcome.DUPLICATE);
        verify(transactions, never()).saveFromNotification(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void ignoraLoQueNoEsUnMovimiento() {
        String token = newToken();

        var outcome = service.ingest(token, "com.whatsapp", "Ana", "hola, como vas", null);

        assertThat(outcome).isEqualTo(NotificationService.Outcome.IGNORED);
        verify(transactions, never()).saveFromNotification(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void usaLaCuentaQueYaTieneDeEseBanco() {
        String token = newToken();
        Account mine = new Account("Mi Nequi", "BILLETERA", "Nequi", new BigDecimal("1000"), ana);
        when(accounts.findByUserUsername("ana")).thenReturn(List.of(mine));

        service.ingest(token, "", "Nequi", "Pagaste $15.000 en Tienda Don Pepe", null);

        ArgumentCaptor<Account> used = ArgumentCaptor.forClass(Account.class);
        verify(transactions).saveFromNotification(used.capture(), any(), eq("EGRESO"), any(), any(), anyString(), any());
        assertThat(used.getValue()).isSameAs(mine);
        verify(accountService, never()).createAccount(any(), any(), any(), any(), any());
    }
}
