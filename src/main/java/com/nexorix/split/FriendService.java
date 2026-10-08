package com.nexorix.split;

import com.nexorix.ai.RateLimiter;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Amigos de Nexorix: buscar por nombre de usuario, enviar, aceptar y quitar. */
@Service
public class FriendService {

    static final int MIN_SEARCH_LENGTH = 3;

    /** Nadie puede recorrer la lista de usuarios probando letras. */
    private final RateLimiter searchLimiter = new RateLimiter(30, Duration.ofMinutes(1));

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    public FriendService(FriendshipRepository friendshipRepository, UserRepository userRepository) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
    }

    /** Lo unico que se muestra de otra persona: su usuario y su nombre. Nunca correo ni cedula. */
    public record Person(String username, String name) {
        static Person of(User user) {
            return new Person(user.getUsername(), user.getName());
        }
    }

    /** NONE, FRIEND, REQUESTED (yo la envie) o INCOMING (me la enviaron). */
    public record SearchResult(String username, String name, String relation) {
    }

    public record Friends(List<Person> friends, List<Person> incoming, List<Person> outgoing) {
    }

    @Transactional(readOnly = true)
    public Friends list(String username) {
        User me = user(username);
        List<Person> friends = new ArrayList<>(), incoming = new ArrayList<>(), outgoing = new ArrayList<>();
        for (Friendship friendship : friendshipRepository.findAllOf(me.getId())) {
            Person other = Person.of(friendship.other(me.getId()));
            if (friendship.isAccepted()) {
                friends.add(other);
            } else if (friendship.getRequester().getId().equals(me.getId())) {
                outgoing.add(other);
            } else {
                incoming.add(other);
            }
        }
        friends.sort((a, b) -> a.username().compareToIgnoreCase(b.username()));
        return new Friends(friends, incoming, outgoing);
    }

    @Transactional(readOnly = true)
    public List<SearchResult> search(String username, String query) {
        User me = user(username);
        String text = query == null ? "" : query.trim().replaceFirst("^@", "");
        if (text.length() < MIN_SEARCH_LENGTH) {
            throw new IllegalArgumentException("Escribe al menos " + MIN_SEARCH_LENGTH + " letras del usuario.");
        }
        if (text.length() > 50) {
            throw new IllegalArgumentException("El usuario es demasiado largo.");
        }
        if (!searchLimiter.tryAcquire(String.valueOf(me.getId()))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiadas búsquedas seguidas. Espera un momento.");
        }

        List<SearchResult> results = new ArrayList<>();
        for (User found : userRepository.findTop10ByUsernameContainingIgnoreCaseAndActiveTrueOrderByUsernameAsc(text)) {
            if (found.getId().equals(me.getId())) {
                continue;
            }
            String relation = friendshipRepository.findBetween(me.getId(), found.getId())
                    .map(f -> f.isAccepted() ? "FRIEND"
                            : f.getRequester().getId().equals(me.getId()) ? "REQUESTED" : "INCOMING")
                    .orElse("NONE");
            results.add(new SearchResult(found.getUsername(), found.getName(), relation));
        }
        return results;
    }

    /** Envia una solicitud. Si la otra persona ya me habia enviado una, quedan como amigos. */
    @Transactional
    public Friends request(String username, String targetUsername) {
        User me = user(username);
        User target = target(targetUsername);
        if (target.getId().equals(me.getId())) {
            throw new IllegalArgumentException("No puedes agregarte a ti mismo.");
        }

        Optional<Friendship> existing = friendshipRepository.findBetween(me.getId(), target.getId());
        if (existing.isPresent()) {
            Friendship friendship = existing.get();
            if (friendship.isAccepted()) {
                throw new IllegalArgumentException("Ya son amigos.");
            }
            if (friendship.getRequester().getId().equals(me.getId())) {
                throw new IllegalArgumentException("Ya le enviaste la solicitud. Falta que la acepte.");
            }
            friendship.accept(); // ya me la habia pedido: queda aceptada
            friendshipRepository.save(friendship);
        } else {
            friendshipRepository.save(new Friendship(me, target));
        }
        return list(username);
    }

    @Transactional
    public Friends accept(String username, String requesterUsername) {
        User me = user(username);
        User requester = target(requesterUsername);
        Friendship friendship = friendshipRepository.findBetween(me.getId(), requester.getId())
                .filter(f -> !f.isAccepted() && f.getAddressee().getId().equals(me.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay una solicitud de esa persona."));
        friendship.accept();
        friendshipRepository.save(friendship);
        return list(username);
    }

    /** Quitar un amigo, rechazar una solicitud o cancelar la que envie. */
    @Transactional
    public Friends remove(String username, String otherUsername) {
        User me = user(username);
        User other = target(otherUsername);
        friendshipRepository.findBetween(me.getId(), other.getId()).ifPresent(friendshipRepository::delete);
        return list(username);
    }

    /** Los usuarios (por nombre de usuario) que SON amigos aceptados de la persona. */
    @Transactional(readOnly = true)
    public List<User> acceptedFriends(User me, List<User> candidates) {
        List<Long> ids = candidates.stream().map(User::getId).toList();
        List<Long> friendIds = friendshipRepository.findAcceptedWith(me.getId(), ids).stream()
                .map(f -> f.other(me.getId()).getId()).toList();
        return candidates.stream().filter(c -> friendIds.contains(c.getId())).toList();
    }

    private User user(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar."));
    }

    private User target(String username) {
        String clean = username == null ? "" : username.trim().replaceFirst("^@", "");
        return userRepository.findByUsername(clean)
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontramos a esa persona."));
    }
}
