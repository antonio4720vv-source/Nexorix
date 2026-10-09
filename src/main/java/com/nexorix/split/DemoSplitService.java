package com.nexorix.split;

import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import com.nexorix.user.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

/**
 * Demo de Dividir gastos: crea dos amigos de ejemplo (si no existen), los hace amigos de la
 * persona y divide una cena entre los tres. Se apaga con NEXORIX_BANK_DEMO=false.
 */
@Service
public class DemoSplitService {

    private static final String[][] FRIENDS = {
            {"Camila Rojas", "demo.camila"}, {"Andrés Mora", "demo.andres"}};

    private final UserRepository users;
    private final UserService userService;
    private final FriendService friends;
    private final SplitService splits;
    private final boolean enabled;

    public DemoSplitService(UserRepository users, UserService userService, FriendService friends,
                            SplitService splits, @Value("${nexorix.bank.demo-enabled:true}") boolean enabled) {
        this.users = users;
        this.userService = userService;
        this.friends = friends;
        this.splits = splits;
        this.enabled = enabled;
    }

    public SplitService.ExpenseView run(String username) {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La demo está apagada.");
        }
        for (String[] f : FRIENDS) {
            String friend = f[1];
            if (users.findByUsername(friend).isEmpty()) {
                userService.createUser(f[0], friend, friend + "@demo.nexorix.local",
                        "9" + Math.abs(friend.hashCode()) % 1_000_000_000L, randomPassword());
            }
            try {
                friends.request(friend, username);
                friends.accept(username, friend);
            } catch (IllegalArgumentException | ResponseStatusException alreadyFriends) {
                // ya eran amigos de una demo anterior
            }
        }
        return splits.create(username, "Cena de ejemplo", new BigDecimal("120000"),
                List.of(FRIENDS[0][1], FRIENDS[1][1]));
    }

    private static String randomPassword() {
        return "Dm" + Long.toString(new SecureRandom().nextLong() & Long.MAX_VALUE, 36).toUpperCase(Locale.ROOT)
                + "-x9!";
    }
}
