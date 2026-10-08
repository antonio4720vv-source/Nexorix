package com.nexorix.transaction;

import com.nexorix.account.Account;
import com.nexorix.account.AccountRepository;
import com.nexorix.importer.TransactionClassifier;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TransactionService {

    /** Monto maximo de un movimiento: un billon de pesos. */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000");

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public TransactionService(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            UserRepository userRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Transaction createTransaction(
            BigDecimal amount,
            String type,
            String description,
            LocalDateTime transactionDate,
            Long accountId,
            String reference,
            String username
    ) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado."));

        Account account = ownedAccount(accountId, user);

        // El monto debe ser positivo y razonable.
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero.");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("El monto es demasiado alto.");
        }

        // Tipo de movimiento.
        String cleanType = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!cleanType.equals("INGRESO") && !cleanType.equals("EGRESO")) {
            throw new IllegalArgumentException("Tipo de movimiento no válido. Usa INGRESO o EGRESO.");
        }

        // Descripcion obligatoria y referencia opcional.
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Escribe una descripción del movimiento.");
        }
        String cleanDescription = description.trim();
        if (cleanDescription.length() > 255) {
            throw new IllegalArgumentException("La descripción tiene máximo 255 caracteres.");
        }

        String cleanReference = reference == null || reference.isBlank() ? null : reference.trim();
        if (cleanReference != null && cleanReference.length() > 150) {
            throw new IllegalArgumentException("La referencia tiene máximo 150 caracteres.");
        }

        BigDecimal cleanAmount = amount.setScale(2, RoundingMode.HALF_UP);

        Transaction transaction = new Transaction(
                cleanAmount,
                cleanType,
                cleanDescription,
                transactionDate,
                account,
                cleanReference
        );
        transaction.setCategory(TransactionClassifier.classify(cleanDescription, cleanType));
        transaction.setSource("MANUAL");

        if (cleanType.equals("INGRESO")) {
            account.setBalance(account.getBalance().add(cleanAmount));
        } else {
            if (account.getBalance().compareTo(cleanAmount) < 0) {
                throw new IllegalArgumentException("Saldo insuficiente para realizar el egreso.");
            }
            account.setBalance(account.getBalance().subtract(cleanAmount));
        }

        accountRepository.save(account);
        return transactionRepository.save(transaction);
    }

    /**
     * Registra una transferencia que la persona hizo entre DOS cuentas suyas:
     * crea la salida en una y la entrada en la otra, con la misma referencia.
     *
     * Nexorix no mueve dinero: solo registra lo que ya paso en los bancos.
     * Todo ocurre en una sola transaccion: si algo falla (por ejemplo,
     * saldo insuficiente), no queda ningun movimiento a medias.
     */
    @Transactional
    public List<Transaction> registerTransfer(
            Long fromAccountId,
            Long toAccountId,
            BigDecimal amount,
            String description,
            String username
    ) {
        if (fromAccountId == null || toAccountId == null) {
            throw new IllegalArgumentException("Elige la cuenta de origen y la de destino.");
        }
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("La cuenta de origen y la de destino deben ser diferentes.");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado."));

        Account from = ownedAccount(fromAccountId, user);
        Account to = ownedAccount(toAccountId, user);

        String note = description == null || description.isBlank() ? null : description.trim();
        String reference = "TRF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        LocalDateTime now = LocalDateTime.now();

        Transaction out = createTransaction(
                amount, "EGRESO",
                note != null ? note : "Envío a " + to.getBank(),
                now, fromAccountId, reference, username);

        Transaction in = createTransaction(
                amount, "INGRESO",
                note != null ? note : "Recibido de " + from.getBank(),
                now, toAccountId, reference, username);

        return List.of(out, in);
    }

    /**
     * Guarda un movimiento que viene de un extracto (PDF o CSV).
     *
     * NO cambia el saldo de la cuenta: el saldo que la persona escribio
     * ya incluye los movimientos de su extracto. Si los sumaramos otra
     * vez, el saldo quedaria duplicado.
     */
    @Transactional
    public Transaction saveImported(
            Account account,
            BigDecimal amount,
            String type,
            String description,
            LocalDateTime transactionDate,
            String reference,
            String category
    ) {
        Transaction transaction = new Transaction(
                amount.setScale(2, RoundingMode.HALF_UP),
                type,
                description,
                transactionDate,
                account,
                reference
        );
        transaction.setCategory(category);
        transaction.setSource("IMPORT");
        return transactionRepository.save(transaction);
    }

    public List<Transaction> getRecentTransactionsByUsername(String username) {
        return transactionRepository.findByAccountUserUsernameOrderByTransactionDateDesc(username);
    }

    /** La cuenta existe y es del usuario; si no, 403 o 400. */
    private Account ownedAccount(Long accountId, User user) {

        if (accountId == null) {
            throw new IllegalArgumentException("Elige una cuenta.");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada."));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No tienes permiso para operar sobre esta cuenta"
            );
        }

        return account;
    }
}
