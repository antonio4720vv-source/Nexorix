package com.nexorix.transaction;

import com.nexorix.account.Account;
import com.nexorix.account.AccountRepository;
import com.nexorix.importer.TransactionClassifier;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class TransactionService {

    /** Monto maximo de un movimiento: un billon de pesos. */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000");

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    @Autowired
    public TransactionService(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            UserRepository userRepository,
            ApplicationEventPublisher events
    ) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.events = events;
    }

    /** Para pruebas: sin avisos de compra. */
    public TransactionService(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            UserRepository userRepository
    ) {
        this(transactionRepository, accountRepository, userRepository, event -> { });
    }

    /**
     * Registra un ingreso o un egreso escrito por la persona. Si es un egreso
     * (una compra), avisa para que Nexorix pregunte por WhatsApp que se compro.
     */
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
        Transaction transaction = create(amount, type, description, transactionDate, accountId, reference, username);

        if (transaction.getType().equals("EGRESO")) {
            Account account = transaction.getAccount();
            events.publishEvent(new PurchaseRegisteredEvent(
                    transaction.getId(), account.getUser().getId(), transaction.getAmount(),
                    transaction.getDescription(), transaction.getTransactionDate()));
        }
        return transaction;
    }

    private Transaction create(
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
            if (account.isCredit() && account.getCreditLimit() != null
                    && account.getBalance().add(cleanAmount).compareTo(account.getCreditLimit()) > 0) {
                throw new IllegalArgumentException("El pago supera lo que debes en la tarjeta de crédito.");
            }
            account.setBalance(account.getBalance().add(cleanAmount));
        } else {
            if (account.getBalance().compareTo(cleanAmount) < 0) {
                throw new IllegalArgumentException(account.isCredit()
                        ? "El cupo disponible de la tarjeta no alcanza para este gasto."
                        : "Saldo insuficiente para realizar el egreso.");
            }
            account.setBalance(account.getBalance().subtract(cleanAmount));
        }

        accountRepository.save(account);
        return transactionRepository.save(transaction);
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

    /**
     * Guarda un movimiento notificado por el banco en vivo (webhook). A diferencia del
     * extracto, aqui el saldo SI cambia: el movimiento es nuevo. El banco manda la
     * verdad, asi que no se rechaza por saldo insuficiente.
     */
    @Transactional
    public Transaction saveFromBank(Account account, BigDecimal amount, String type, String description,
                                    LocalDateTime transactionDate, String reference) {
        BigDecimal clean = amount.setScale(2, RoundingMode.HALF_UP);
        Transaction transaction = new Transaction(clean, type, description, transactionDate, account, reference);
        transaction.setCategory(TransactionClassifier.classify(description, type));
        transaction.setSource("BANK");
        // El saldo nunca baja de cero (ni en credito pasa del cupo): el banco manda, pero no hay dinero negativo.
        account.applyClamped(type.equals("INGRESO") ? clean : clean.negate());
        accountRepository.save(account);
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
