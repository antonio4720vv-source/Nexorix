package com.nexorix.account;

import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    private AccountRepository accountRepository;
    private AccountService service;
    private User ana;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        service = new AccountService(accountRepository, userRepository);

        ana = new User("Ana", "ana", "ana@nexorix.com", "1", "hash");
        ReflectionTestUtils.setField(ana, "id", 1L);
        when(userRepository.findByUsername("ana")).thenReturn(Optional.of(ana));
        when(accountRepository.findByUserUsername("ana")).thenReturn(List.of());
        when(accountRepository.save(any(Account.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void creaLaCuentaLimpiandoLosDatos() {
        Account account = service.createAccount("  Nequi Ana ", "billetera", " Nequi ",
                new BigDecimal("1500000"), "ana");

        assertThat(account.getName()).isEqualTo("Nequi Ana");
        assertThat(account.getBank()).isEqualTo("Nequi");
        assertThat(account.getType()).isEqualTo("BILLETERA");
        assertThat(account.getBalance()).isEqualByComparingTo("1500000");
    }

    @Test
    void noAceptaSaldoNegativo() {
        assertThatThrownBy(() -> service.createAccount("Nequi", "AHORROS", "Nequi",
                new BigDecimal("-1"), "ana"))
                .hasMessageContaining("negativo");
        verify(accountRepository, never()).save(any());
    }

    @Test
    void noAceptaNombreVacio() {
        assertThatThrownBy(() -> service.createAccount("  ", "AHORROS", "Nequi",
                BigDecimal.ZERO, "ana"))
                .hasMessageContaining("nombre");
    }

    @Test
    void noAceptaUnTipoInventado() {
        assertThatThrownBy(() -> service.createAccount("Nequi", "CRIPTO", "Nequi",
                BigDecimal.ZERO, "ana"))
                .hasMessageContaining("Tipo de cuenta");
    }

    @Test
    void noAceptaUnaCuentaRepetida() {
        Account existente = new Account("Nequi Ana", "AHORROS", "Nequi", BigDecimal.ZERO, ana);
        when(accountRepository.findByUserUsername("ana")).thenReturn(List.of(existente));

        assertThatThrownBy(() -> service.createAccount("nequi ana", "AHORROS", "NEQUI",
                BigDecimal.ZERO, "ana"))
                .hasMessageContaining("Ya tienes una cuenta");
    }
}
