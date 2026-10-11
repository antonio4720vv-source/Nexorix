package com.nexorix.transaction;

import com.nexorix.account.Account;
import com.nexorix.account.AccountRepository;
import com.nexorix.importer.TransactionClassifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransactionService {

    /** Monto maximo de un movimiento: un billon de pesos. */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000");

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public TransactionService(TransactionRepository transactionRepository, AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
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
}
