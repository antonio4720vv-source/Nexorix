package com.nexorix.fraud;

import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import com.nexorix.whatsapp.PurchaseNoteService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/** Preferencias de notificacion, perfil de comportamiento y la campanita de la app. */
@Service
public class SecurityProfileService {

    public record Preferences(boolean whatsappNotificationsEnabled, String securityPhone) {
    }

    public record Count(String name, long count) {
    }

    public record Profile(long observations, List<Count> merchants, List<Count> categories,
                          List<Count> recipients, List<Count> places) {
    }

    public record NotificationView(Long id, String severity, String title, String body, String channels,
                                   boolean read, LocalDateTime createdAt, Long bankEventId) {
    }

    private static final Pageable TOP = PageRequest.of(0, 5);

    private final UserRepository users;
    private final UserBehaviorLogRepository logs;
    private final AppNotificationRepository notifications;

    public SecurityProfileService(UserRepository users, UserBehaviorLogRepository logs,
                                  AppNotificationRepository notifications) {
        this.users = users;
        this.logs = logs;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public Preferences preferences(String username) {
        User user = user(username);
        return new Preferences(user.isWhatsappNotificationsEnabled(), user.getSecurityPhone());
    }

    /** phone == null no toca el celular; "" lo borra. */
    @Transactional
    public Preferences save(String username, Boolean whatsappEnabled, String phone) {
        User user = user(username);
        if (whatsappEnabled != null) {
            user.setWhatsappNotificationsEnabled(whatsappEnabled);
        }
        if (phone != null) {
            user.setSecurityPhone(phone.isBlank() ? null : PurchaseNoteService.normalizePhone(phone));
        }
        users.save(user);
        return new Preferences(user.isWhatsappNotificationsEnabled(), user.getSecurityPhone());
    }

    @Transactional(readOnly = true)
    public Profile profile(String username) {
        Long id = user(username).getId();
        return new Profile(logs.countByUserId(id),
                counts(logs.topMerchants(id, TOP)), counts(logs.topCategories(id, TOP)),
                counts(logs.topRecipients(id, TOP)),
                logs.topPlaces(id, TOP).stream()
                        .map(r -> new Count(new Geo((String) r[0], (String) r[1], 0, 0).label(), (Long) r[2])).toList());
    }

    @Transactional(readOnly = true)
    public List<NotificationView> notifications(String username) {
        return notifications.findByUserIdOrderByCreatedAtDescIdDesc(user(username).getId(), PageRequest.of(0, 50))
                .stream()
                .map(n -> new NotificationView(n.getId(), n.getSeverity().name(), n.getTitle(), n.getBody(),
                        n.getChannels(), n.isRead(), n.getCreatedAt(), n.getBankEventId()))
                .toList();
    }

    public long unread(String username) {
        return notifications.countByUserIdAndReadFalse(user(username).getId());
    }

    @Transactional
    public void markRead(String username, Long id) {
        notifications.findByIdAndUserId(id, user(username).getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aviso no encontrado."))
                .markRead();
    }

    private static List<Count> counts(List<Object[]> rows) {
        return rows.stream().map(r -> new Count(String.valueOf(r[0]), (Long) r[1])).toList();
    }

    private User user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar."));
    }
}
