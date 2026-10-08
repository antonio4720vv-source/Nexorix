package com.nexorix.split;

import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FriendServiceTest {

    private FriendshipRepository friendshipRepository;
    private UserRepository userRepository;
    private FriendService service;
    private User ana, beto, carla;

    private static User user(String username, long id) {
        User user = new User("Nombre " + username, username, username + "@nexorix.com", String.valueOf(id), "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @BeforeEach
    void setUp() {
        friendshipRepository = mock(FriendshipRepository.class);
        userRepository = mock(UserRepository.class);
        service = new FriendService(friendshipRepository, userRepository);

        ana = user("ana_g", 1);
        beto = user("beto_r", 2);
        carla = user("carla_m", 3);
        for (User u : List.of(ana, beto, carla)) {
            when(userRepository.findByUsername(u.getUsername())).thenReturn(Optional.of(u));
        }
        when(friendshipRepository.findAllOf(1L)).thenReturn(List.of());
        when(friendshipRepository.findBetween(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void elBuscadorPideAlMenosTresLetrasYNoMuestraDatosPrivados() {
        assertThatThrownBy(() -> service.search("ana_g", "be")).hasMessageContaining("al menos 3");

        when(userRepository.findTop10ByUsernameContainingIgnoreCaseAndActiveTrueOrderByUsernameAsc("_r"))
                .thenReturn(List.of(beto));
        when(userRepository.findTop10ByUsernameContainingIgnoreCaseAndActiveTrueOrderByUsernameAsc("eto"))
                .thenReturn(List.of(beto, ana)); // incluye a quien busca

        List<FriendService.SearchResult> found = service.search("ana_g", "@eto");

        assertThat(found).hasSize(1); // no se incluye a si misma
        assertThat(found.get(0).username()).isEqualTo("beto_r");
        assertThat(found.get(0).relation()).isEqualTo("NONE");
        assertThat(found.get(0).toString()).doesNotContain("nexorix.com");
    }

    @Test
    void elBuscadorTieneLimiteParaNoRecorrerLaListaDeUsuarios() {
        when(userRepository.findTop10ByUsernameContainingIgnoreCaseAndActiveTrueOrderByUsernameAsc(any()))
                .thenReturn(List.of());
        for (int i = 0; i < 30; i++) service.search("ana_g", "abc");

        assertThatThrownBy(() -> service.search("ana_g", "abc"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Demasiadas");
    }

    @Test
    void enviaUnaSolicitudPendiente() {
        service.request("ana_g", "beto_r");

        org.mockito.ArgumentCaptor<Friendship> captor = org.mockito.ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(Friendship.PENDING);
        assertThat(captor.getValue().getRequester()).isSameAs(ana);
    }

    @Test
    void siLaOtraPersonaYaMeLaHabiaEnviadoQuedanComoAmigos() {
        Friendship incoming = new Friendship(beto, ana);
        when(friendshipRepository.findBetween(1L, 2L)).thenReturn(Optional.of(incoming));

        service.request("ana_g", "beto_r");

        assertThat(incoming.isAccepted()).isTrue();
    }

    @Test
    void noPermiteAgregarseASiMismoNiRepetirSolicitudes() {
        assertThatThrownBy(() -> service.request("ana_g", "ana_g")).hasMessageContaining("ti mismo");

        when(friendshipRepository.findBetween(1L, 2L)).thenReturn(Optional.of(new Friendship(ana, beto)));
        assertThatThrownBy(() -> service.request("ana_g", "beto_r")).hasMessageContaining("Ya le enviaste");
        verify(friendshipRepository, never()).save(any());
    }

    @Test
    void soloQuienRecibeLaSolicitudPuedeAceptarla() {
        Friendship sent = new Friendship(ana, beto);
        when(friendshipRepository.findBetween(1L, 2L)).thenReturn(Optional.of(sent));

        // Ana la envio: no puede aceptarla ella misma.
        assertThatThrownBy(() -> service.accept("ana_g", "beto_r")).isInstanceOf(ResponseStatusException.class);
        assertThat(sent.isAccepted()).isFalse();

        when(friendshipRepository.findBetween(2L, 1L)).thenReturn(Optional.of(sent));
        when(friendshipRepository.findAllOf(2L)).thenReturn(List.of());
        service.accept("beto_r", "ana_g");
        assertThat(sent.isAccepted()).isTrue();
    }

    @Test
    void clasificaAmigosYSolicitudes() {
        Friendship accepted = new Friendship(ana, beto);
        accepted.accept();
        Friendship incoming = new Friendship(carla, ana);
        when(friendshipRepository.findAllOf(1L)).thenReturn(List.of(accepted, incoming));

        FriendService.Friends friends = service.list("ana_g");

        assertThat(friends.friends()).extracting(FriendService.Person::username).containsExactly("beto_r");
        assertThat(friends.incoming()).extracting(FriendService.Person::username).containsExactly("carla_m");
        assertThat(friends.outgoing()).isEmpty();
    }

    @Test
    void unUsuarioInexistenteDa404() {
        assertThatThrownBy(() -> service.request("ana_g", "fantasma"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("No encontramos");
    }
}
