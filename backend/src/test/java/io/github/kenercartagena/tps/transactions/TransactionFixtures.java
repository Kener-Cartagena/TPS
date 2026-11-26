package io.github.kenercartagena.tps.transactions;

import io.github.kenercartagena.tps.accounts.application.AccountHolderService;
import io.github.kenercartagena.tps.accounts.application.AccountService;
import io.github.kenercartagena.tps.accounts.application.AccountView;
import io.github.kenercartagena.tps.accounts.application.OpenAccountCommand;
import io.github.kenercartagena.tps.accounts.application.RegisterAccountHolderCommand;
import io.github.kenercartagena.tps.accounts.domain.AccountType;
import io.github.kenercartagena.tps.accounts.domain.DocumentType;
import io.github.kenercartagena.tps.accounts.domain.HolderType;
import io.github.kenercartagena.tps.shared.money.Currency;
import io.github.kenercartagena.tps.shared.money.Money;
import io.github.kenercartagena.tps.transactions.application.TransactionEngine;
import io.github.kenercartagena.tps.transactions.application.TransactionOutcome;
import io.github.kenercartagena.tps.transactions.application.TransactionRequest;
import io.github.kenercartagena.tps.transactions.domain.IdempotencyKey;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Accounts and transactions for tests that call the engine directly, without HTTP. The
 * actor is the bootstrap administrator: {@code created_by} must be a real user, and the
 * engine does not check permissions (the web layer does).
 */
final class TransactionFixtures {

    private final AccountHolderService holders;
    private final AccountService accounts;
    private final TransactionEngine engine;
    private final UUID actorId;

    TransactionFixtures(AccountHolderService holders, AccountService accounts, TransactionEngine engine,
            JdbcTemplate jdbc) {
        this.holders = holders;
        this.accounts = accounts;
        this.engine = engine;
        this.actorId = jdbc.queryForObject("select id from users order by created_at limit 1", UUID.class);
    }

    UUID actorId() {
        return actorId;
    }

    /** A new, active HNL account with this balance (deposited through the engine). */
    AccountView accountWith(String balance) {
        String dni = "0801" + ThreadLocalRandom.current().nextLong(100_000_000L, 1_000_000_000L);
        UUID holderId = holders.register(new RegisterAccountHolderCommand(HolderType.PERSON,
                "Engine Test", DocumentType.DNI, dni), actorId).id();
        AccountView account = accounts.open(new OpenAccountCommand(holderId, AccountType.SAVINGS,
                Currency.HNL), actorId);
        if (Money.of(balance, Currency.HNL).isPositive()) {
            submit(TransactionRequest.deposit(account.id(), hnl(balance), null), newKey());
        }
        return account;
    }

    TransactionOutcome submit(TransactionRequest request, IdempotencyKey key) {
        return engine.submit(request, key, actorId);
    }

    Money balanceOf(AccountView account) {
        return Money.of(accounts.balance(account.id()).ledgerBalance(), Currency.HNL);
    }

    static Money hnl(String amount) {
        return Money.of(amount, Currency.HNL);
    }

    static IdempotencyKey newKey() {
        return new IdempotencyKey(UUID.randomUUID().toString());
    }
}
