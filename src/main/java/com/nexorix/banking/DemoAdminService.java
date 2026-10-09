package com.nexorix.banking;

import com.nexorix.account.Account;
import com.nexorix.account.AccountRepository;
import com.nexorix.trace.ReconciliationMatch;
import com.nexorix.trace.ReconciliationMatchRepository;
import com.nexorix.transaction.Transaction;
import com.nexorix.transaction.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Herramientas SOLO de la demo: agregar dinero, reiniciar el dinero y reiniciar los movimientos
 * de una cuenta. Piden una clave de demo (NEXORIX_DEMO_PASSWORD) y se apagan con NEXORIX_BANK_DEMO=false.
 */
@Service
public class DemoAdminService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final ReconciliationMatchRepository matches;
    private final String password;
    private final boolean enabled;

    public DemoAdminService(AccountRepository accounts, TransactionRepository transactions,
                            ReconciliationMatchRepository matches,
                            @Value("${nexorix.demo.password:1052839301}") String password,
                            @Value("${nexorix.bank.demo-enabled:true}") boolean enabled) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.matches = matches;
        this.password = password;
        this.enabled = enabled;
    }

    @Transactional
    public Account addMoney(String username, String pass, Long accountId, BigDecimal amount) {
        check(pass);
        if (amount == null || amount.signum() <= 0 || amount.compareTo(new BigDecimal("1000000000000")) > 0) {
            throw new IllegalArgumentException("Escribe un monto válido para agregar.");
        }
        Account account = owned(username, accountId);
        account.applyClamped(amount.setScale(2, RoundingMode.HALF_UP));
        return accounts.save(account);
    }

    /** Deja el dinero en cero (en una tarjeta de credito, el cupo vuelve a estar completo). */
    @Transactional
    public int resetMoney(String username, String pass) {
        check(pass);
        List<Account> mine = accounts.findByUserUsername(username);
        for (Account a : mine) {
            a.setBalance(a.isCredit() && a.getCreditLimit() != null ? a.getCreditLimit() : BigDecimal.ZERO.setScale(2));
        }
        accounts.saveAll(mine);
        return mine.size();
    }

    /** Borra todos los movimientos de una cuenta (y las conciliaciones de Trace que los usan). */
    @Transactional
    public int resetTransactions(String username, String pass, Long accountId) {
        check(pass);
        Account account = owned(username, accountId);
        List<Transaction> own = transactions.findByAccountUserUsernameOrderByTransactionDateDesc(username).stream()
                .filter(t -> t.getAccount().getId().equals(account.getId())).toList();
        Set<ReconciliationMatch> used = new LinkedHashSet<>();
        for (Transaction t : own) {
            used.addAll(matches.findByOriginTransactionId(t.getId()));
            used.addAll(matches.findByDestinationTransactionId(t.getId()));
        }
        matches.deleteAll(used);
        matches.flush();
        transactions.deleteAll(own);
        return own.size();
    }

    private void check(String pass) {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La demo está apagada.");
        }
        byte[] given = pass == null ? new byte[0] : pass.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(given, password.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Clave de demo incorrecta.");
        }
    }

    private Account owned(String username, Long accountId) {
        return accounts.findById(accountId == null ? -1L : accountId)
                .filter(a -> a.getUser().getUsername().equals(username))
                .orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada."));
    }
}
