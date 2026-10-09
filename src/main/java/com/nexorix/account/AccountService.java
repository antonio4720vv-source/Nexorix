package com.nexorix.account;

import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AccountService {

    /** Tipos de cuenta permitidos. */
    public static final Set<String> TYPES = Set.of("AHORROS", "CORRIENTE", "BILLETERA", "DEBITO", "CREDITO");

    /** Maximo de cuentas por persona (evita abusos). */
    public static final int MAX_ACCOUNTS = 20;

    /** Saldo maximo permitido: un billon de pesos. */
    public static final BigDecimal MAX_BALANCE = new BigDecimal("1000000000000");

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountService(
            AccountRepository accountRepository,
            UserRepository userRepository
    ) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Account createAccount(
            String name,
            String type,
            String bank,
            BigDecimal balance,
            String username
    ) {
        return createAccount(name, type, bank, balance, username, null, null);
    }

    /** Igual, pero con los datos de la tarjeta (numero y vencimiento): se validan y solo se guardan marca, ultimos 4 y vencimiento. */
    @Transactional
    public Account createAccount(String name, String type, String bank, BigDecimal balance, String username,
                                 BigDecimal creditLimit, String cardNumber, String cardExpiry) {
        boolean card = cardNumber != null && !cardNumber.isBlank();
        CardInfo info = card ? CardInfo.of(cardNumber, cardExpiry, java.time.LocalDate.now()) : null;
        Account account = createAccount(name, type, bank, balance, username, creditLimit,
                info == null ? null : info.last4());
        if (info != null) {
            account.setCard(info.brand(), info.last4(), info.expMonth(), info.expYear());
            return accountRepository.save(account);
        }
        return account;
    }

    /**
     * Crea una cuenta o tarjeta. En CREDITO "balance" es el cupo disponible y creditLimit el cupo total
     * (si no viene, es igual al disponible). Ninguna cuenta puede quedar con saldo negativo.
     */
    @Transactional
    public Account createAccount(
            String name,
            String type,
            String bank,
            BigDecimal balance,
            String username,
            BigDecimal creditLimit,
            String cardLast4
    ) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        String cleanName = text(name, "El nombre de la cuenta es obligatorio.", 100);
        String cleanBank = text(bank, "El banco o billetera es obligatorio.", 100);

        String cleanType = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(cleanType)) {
            throw new IllegalArgumentException(
                    "Tipo de cuenta no válido. Usa AHORROS, CORRIENTE, BILLETERA, DEBITO o CREDITO.");
        }

        if (balance == null || balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El saldo inicial no puede ser negativo.");
        }

        if (balance.compareTo(MAX_BALANCE) > 0) {
            throw new IllegalArgumentException("El saldo inicial es demasiado alto.");
        }

        String last4 = cardLast4 == null || cardLast4.isBlank() ? null : cardLast4.trim();
        if (last4 != null && !last4.matches("\\d{4}")) {
            throw new IllegalArgumentException("Los últimos dígitos de la tarjeta son 4 números.");
        }
        BigDecimal limit = null;
        if (cleanType.equals("CREDITO")) {
            limit = creditLimit == null ? balance : creditLimit;
            if (limit.compareTo(BigDecimal.ZERO) <= 0 || limit.compareTo(MAX_BALANCE) > 0) {
                throw new IllegalArgumentException("El cupo de la tarjeta de crédito no es válido.");
            }
            if (balance.compareTo(limit) > 0) {
                throw new IllegalArgumentException("El cupo disponible no puede superar el cupo total.");
            }
        }

        List<Account> existing = accountRepository.findByUserUsername(username);

        if (existing.size() >= MAX_ACCOUNTS) {
            throw new IllegalArgumentException(
                    "Ya tienes " + MAX_ACCOUNTS + " cuentas, que es el máximo.");
        }

        boolean duplicated = existing.stream().anyMatch(account ->
                account.getName().equalsIgnoreCase(cleanName)
                        && account.getBank().equalsIgnoreCase(cleanBank));

        if (duplicated) {
            throw new IllegalArgumentException("Ya tienes una cuenta con ese nombre en ese banco.");
        }

        Account account = new Account(
                cleanName,
                cleanType,
                cleanBank,
                balance.setScale(2, RoundingMode.HALF_UP),
                user
        );

        account.setCreditLimit(limit == null ? null : limit.setScale(2, RoundingMode.HALF_UP));
        account.setCardLast4(last4);

        return accountRepository.save(account);
    }

    public List<Account> getAccountsByUsername(String username) {
        return accountRepository.findByUserUsername(username);
    }

    private static String text(String value, String message, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String clean = value.trim();
        if (clean.length() > max) {
            throw new IllegalArgumentException("Máximo " + max + " caracteres.");
        }
        return clean;
    }
}
