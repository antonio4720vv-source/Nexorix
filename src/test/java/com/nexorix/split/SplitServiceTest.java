package com.nexorix.split;

import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SplitServiceTest {

    private SplitExpenseRepository expenseRepository;
    private SplitShareRepository shareRepository;
    private FriendService friendService;
    private SplitService service;

    private User ana, beto, carla, extrano;
    private final List<SplitShare> saved = new ArrayList<>();

    private static User user(String username, long id) {
        User user = new User(username, username, username + "@nexorix.com", String.valueOf(id), "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @BeforeEach
    void setUp() {
        expenseRepository = mock(SplitExpenseRepository.class);
        shareRepository = mock(SplitShareRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        friendService = mock(FriendService.class);
        service = new SplitService(expenseRepository, shareRepository, userRepository, friendService, "https://nexorix.test/");

        ana = user("ana", 1);
        beto = user("beto", 2);
        carla = user("carla", 3);
        extrano = user("extrano", 4);
        for (User u : List.of(ana, beto, carla, extrano)) {
            when(userRepository.findByUsername(u.getUsername())).thenReturn(Optional.of(u));
        }
        when(friendService.acceptedFriends(any(), anyList())).thenAnswer(call -> {
            List<User> candidates = call.getArgument(1);
            return candidates.stream().filter(u -> u != extrano).toList(); // "extrano" no es amigo de Ana
        });
        when(expenseRepository.save(any(SplitExpense.class))).thenAnswer(call -> {
            SplitExpense e = call.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 10L);
            return e;
        });
        when(shareRepository.saveAll(anyList())).thenAnswer(call -> {
            List<SplitShare> shares = call.getArgument(0);
            long id = 100;
            for (SplitShare s : shares) ReflectionTestUtils.setField(s, "id", id++);
            saved.addAll(shares);
            return shares;
        });
    }

    // ---------------- la cuenta ----------------

    @Test
    void divideEnPartesIgualesEntreElPagadorYSusAmigos() {
        SplitService.Split split = SplitService.divide(new BigDecimal("90000"), 2); // 3 personas
        assertThat(split.each()).isEqualByComparingTo("30000");
        assertThat(split.payerShare()).isEqualByComparingTo("30000");
    }

    @Test
    void losCentavosSobrantesLosAbsorbeElPagador() {
        SplitService.Split split = SplitService.divide(new BigDecimal("100000"), 2); // 3 personas
        assertThat(split.each()).isEqualByComparingTo("33333.33");
        assertThat(split.payerShare()).isEqualByComparingTo("33333.34");
    }

    @Test
    void nuncaSePierdeNiSeInventaPlata() {
        for (int friends = 1; friends <= 20; friends++) {
            for (String total : List.of("1", "0.05", "100000", "99999.99", "1234567.89", "7")) {
                BigDecimal amount = new BigDecimal(total);
                if (amount.movePointRight(2).longValue() < friends + 1) continue; // muy pequeno
                SplitService.Split split = SplitService.divide(amount, friends);
                BigDecimal sum = split.each().multiply(BigDecimal.valueOf(friends)).add(split.payerShare());
                assertThat(sum).as("total %s entre %s amigos", total, friends).isEqualByComparingTo(amount);
                assertThat(split.payerShare().subtract(split.each()).abs()).isLessThan(new BigDecimal("0.20"));
            }
        }
    }

    @Test
    void rechazaMontosInvalidos() {
        assertThatThrownBy(() -> SplitService.divide(BigDecimal.ZERO, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SplitService.divide(new BigDecimal("-5"), 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SplitService.divide(new BigDecimal("100"), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SplitService.divide(new BigDecimal("0.02"), 5)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------- crear ----------------

    @Test
    void creaUnCobroParaCadaAmigoConSuEnlace() {
        SplitService.ExpenseView view = service.create("ana", "Cena", new BigDecimal("90000"), List.of("beto", "carla"));

        assertThat(view.shares()).hasSize(2);
        assertThat(view.shares()).allSatisfy(s -> {
            assertThat(new BigDecimal(s.amount())).isEqualByComparingTo("30000");
            assertThat(s.status()).isEqualTo(SplitShare.PENDING);
            assertThat(s.link()).startsWith("https://nexorix.test/cobro.html?t=").hasSizeGreaterThan(50);
        });
        assertThat(view.shares().get(0).link()).isNotEqualTo(view.shares().get(1).link());
        assertThat(new BigDecimal(view.pending())).isEqualByComparingTo("60000"); // lo que le deben a Ana
        assertThat(new BigDecimal(view.payerShare())).isEqualByComparingTo("30000");
    }

    @Test
    void soloSePuedeDividirConAmigos() {
        assertThatThrownBy(() -> service.create("ana", "Cena", new BigDecimal("90000"), List.of("beto", "extrano")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extrano");
    }

    @Test
    void noPuedeElegirseASiMismoNiDejarLaListaVacia() {
        assertThatThrownBy(() -> service.create("ana", "x", BigDecimal.TEN, List.of("ana")))
                .hasMessageContaining("ya cuentas");
        assertThatThrownBy(() -> service.create("ana", "x", BigDecimal.TEN, List.of()))
                .hasMessageContaining("al menos un amigo");
        assertThatThrownBy(() -> service.create("ana", "x", BigDecimal.TEN, null))
                .hasMessageContaining("al menos un amigo");
    }

    @Test
    void validaElMontoYElNombre() {
        assertThatThrownBy(() -> service.create("ana", "x", BigDecimal.ZERO, List.of("beto")))
                .hasMessageContaining("mayor que cero");
        assertThatThrownBy(() -> service.create("ana", "x".repeat(101), BigDecimal.TEN, List.of("beto")))
                .hasMessageContaining("100 caracteres");
        assertThatThrownBy(() -> service.create("ana", "x", new BigDecimal("2000000000000"), List.of("beto")))
                .hasMessageContaining("demasiado alto");
    }

    @Test
    void unUsuarioRepetidoSeCobraUnaSolaVez() {
        SplitService.ExpenseView view = service.create("ana", null, new BigDecimal("100"), List.of("beto", "beto", " beto "));
        assertThat(view.shares()).hasSize(1);
        assertThat(view.title()).isEqualTo("Cuenta compartida");
        assertThat(new BigDecimal(view.shares().get(0).amount())).isEqualByComparingTo("50");
    }

    // ---------------- cobrar ----------------

    private SplitShare shareOf(User participant, User payer, String status) {
        SplitExpense expense = new SplitExpense(payer, "Cena", new BigDecimal("90000"), new BigDecimal("30000"));
        ReflectionTestUtils.setField(expense, "id", 10L);
        SplitShare share = new SplitShare(expense, participant, new BigDecimal("30000"), "tok-beto");
        ReflectionTestUtils.setField(share, "id", 100L);
        if (SplitShare.SETTLED.equals(status)) share.settle();
        when(shareRepository.findByToken("tok-beto")).thenReturn(Optional.of(share));
        when(shareRepository.findWithExpense(100L)).thenReturn(Optional.of(share));
        when(shareRepository.save(any(SplitShare.class))).thenAnswer(call -> call.getArgument(0));
        return share;
    }

    @Test
    void elEnlaceSoloLoVenElPagadorYQuienDebe() {
        shareOf(beto, ana, SplitShare.PENDING);

        assertThat(service.collect("beto", "tok-beto").iAmPayer()).isFalse();
        assertThat(service.collect("ana", "tok-beto").iAmPayer()).isTrue();
        assertThat(service.collect("beto", "tok-beto").payerUsername()).isEqualTo("ana");

        assertThatThrownBy(() -> service.collect("carla", "tok-beto")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.collect("beto", "no-existe")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.collect("beto", "x".repeat(200))).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void quienDebePuedeAvisarQueYaPagoPeroElPagadorNo() {
        SplitShare share = shareOf(beto, ana, SplitShare.PENDING);

        assertThatThrownBy(() -> service.reportPaid("ana", "tok-beto"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("quien debe");
        assertThat(share.getStatus()).isEqualTo(SplitShare.PENDING);

        service.reportPaid("beto", "tok-beto");
        assertThat(share.getStatus()).isEqualTo(SplitShare.REPORTED);
        assertThat(share.getReportedAt()).isNotNull();
    }

    @Test
    void soloElPagadorPuedeSaldar() {
        SplitShare share = shareOf(beto, ana, SplitShare.REPORTED);

        assertThatThrownBy(() -> service.settle("beto", 100L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.settle("carla", 100L)).isInstanceOf(ResponseStatusException.class);
        assertThat(share.getStatus()).isNotEqualTo(SplitShare.SETTLED);

        assertThat(service.settle("ana", 100L).status()).isEqualTo(SplitShare.SETTLED);
        assertThat(share.getSettledAt()).isNotNull();
    }

    @Test
    void unaVezSaldadoYaPagoNoVuelveAReportado() {
        SplitShare share = shareOf(beto, ana, SplitShare.SETTLED);
        service.reportPaid("beto", "tok-beto");
        assertThat(share.getStatus()).isEqualTo(SplitShare.SETTLED);
    }

    @Test
    void unaCuentaCanceladaNoSeCobra() {
        SplitShare share = shareOf(beto, ana, SplitShare.PENDING);
        share.getExpense().cancel();

        assertThatThrownBy(() -> service.reportPaid("beto", "tok-beto")).hasMessageContaining("cancelada");
        assertThatThrownBy(() -> service.settle("ana", 100L)).hasMessageContaining("cancelada");
        assertThat(service.collect("beto", "tok-beto").cancelled()).isTrue();
    }

    @Test
    void soloElPagadorCancelaSuCuenta() {
        when(expenseRepository.findByIdAndPayerId(10L, 2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancel("beto", 10L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void losTokensSonLargosYDistintos() {
        String a = SplitService.newToken(), b = SplitService.newToken();
        assertThat(a).isNotEqualTo(b).hasSize(32).matches("[A-Za-z0-9_-]+");
    }
}
