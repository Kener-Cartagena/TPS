package io.github.kenercartagena.tps.transactions;

import static io.github.kenercartagena.tps.transactions.TransactionFixtures.hnl;
import static io.github.kenercartagena.tps.transactions.TransactionFixtures.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kenercartagena.tps.TestcontainersConfiguration;
import io.github.kenercartagena.tps.accounts.application.AccountHolderService;
import io.github.kenercartagena.tps.accounts.application.AccountService;
import io.github.kenercartagena.tps.accounts.application.AccountView;
import io.github.kenercartagena.tps.transactions.application.TransactionEngine;
import io.github.kenercartagena.tps.transactions.application.TransactionRequest;
import io.github.kenercartagena.tps.transactions.application.TransactionView;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Transaction rules that PostgreSQL enforces by itself (migration {@code V6}), checked with
 * SQL that bypasses the application. Each statement runs in a transaction that is rolled
 * back. CHECK constraints are evaluated before foreign keys, so inserted rows may reference
 * ids that do not exist: the CHECK under test is what rejects them ({@code 23514}).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransactionsDatabaseConstraintsTests {

    private static final String CHECK_VIOLATION = "23514";
    private static final String UNIQUE_VIOLATION = "23505";
    /** {@code restrict_violation}, raised by the guard triggers. */
    private static final String RESTRICT_VIOLATION = "23001";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AccountHolderService holders;

    @Autowired
    private AccountService accounts;

    @Autowired
    private TransactionEngine engine;

    @Autowired
    private JdbcTemplate jdbc;

    private TransactionFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new TransactionFixtures(holders, accounts, engine, jdbc);
    }

    // ---- CHECK constraints -----------------------------------------------------------

    @Test
    void eachTypeUsesOnlyItsOwnAccounts() {
        assertRejected(CHECK_VIOLATION, insert("DEPOSIT", "CREATED", "10.00", "'%s'".formatted(UUID.randomUUID()), "NULL"));
        assertRejected(CHECK_VIOLATION, insert("WITHDRAWAL", "CREATED", "10.00", "NULL", "'%s'".formatted(UUID.randomUUID())));
        assertRejected(CHECK_VIOLATION, insert("TRANSFER", "CREATED", "10.00", "NULL", "'%s'".formatted(UUID.randomUUID())));
    }

    @Test
    void theAmountIsPositive() {
        assertRejected(CHECK_VIOLATION, deposit("CREATED", "0.00"));
        assertRejected(CHECK_VIOLATION, deposit("CREATED", "-1.00"));
    }

    @Test
    void aCompletedTransactionHasItsJournalEntryAndOutcomeTime() {
        // COMPLETED without journal entry nor processed_at.
        assertRejected(CHECK_VIOLATION, deposit("COMPLETED", "10.00"));
    }

    @Test
    void aRejectedTransactionSaysWhy() {
        // REJECTED without rejection code nor processed_at.
        assertRejected(CHECK_VIOLATION, deposit("REJECTED", "10.00"));
    }

    // ---- Uniqueness and triggers -----------------------------------------------------

    @Test
    void anIdempotencyKeyIsUsedOncePerUser() {
        AccountView account = fixtures.accountWith("0");
        TransactionView stored = completedDeposit(account);

        assertRejected(UNIQUE_VIOLATION, """
                INSERT INTO transactions (id, type, status, amount, currency, destination_account_id,
                                          idempotency_key, request_hash, created_by, created_at, updated_at)
                SELECT '%s', 'DEPOSIT', 'CREATED', 5, 'HNL', destination_account_id,
                       idempotency_key, request_hash, created_by, now(), now()
                FROM transactions WHERE id = '%s'""".formatted(UUID.randomUUID(), stored.id()));
    }

    @Test
    void whatWasRequestedNeverChanges() {
        TransactionView stored = completedDeposit(fixtures.accountWith("0"));

        assertRejected(RESTRICT_VIOLATION,
                "UPDATE transactions SET amount = 1000000 WHERE id = '%s'".formatted(stored.id()));
        assertRejected(RESTRICT_VIOLATION,
                "UPDATE transactions SET created_by = NULL WHERE id = '%s'".formatted(stored.id()));
    }

    @Test
    void aFinalStatusIsFinal() {
        TransactionView completed = completedDeposit(fixtures.accountWith("0"));
        TransactionView rejected = fixtures.submit(TransactionRequest.withdrawal(
                fixtures.accountWith("0").id(), hnl("1.00"), null), newKey()).transaction();
        assertThat(rejected.status().name()).isEqualTo("REJECTED");

        assertRejected(RESTRICT_VIOLATION, """
                UPDATE transactions SET status = 'REJECTED', rejection_code = 'X', rejection_detail = 'X',
                       journal_entry_id = NULL WHERE id = '%s'""".formatted(completed.id()));
        assertRejected(RESTRICT_VIOLATION, """
                UPDATE transactions SET status = 'CREATED', rejection_code = NULL, rejection_detail = NULL,
                       processed_at = NULL WHERE id = '%s'""".formatted(rejected.id()));
    }

    @Test
    void transactionsAndTheirHistoryAreNeverDeleted() {
        TransactionView stored = completedDeposit(fixtures.accountWith("0"));

        assertRejected(RESTRICT_VIOLATION, "DELETE FROM transactions WHERE id = '%s'".formatted(stored.id()));
        assertRejected(RESTRICT_VIOLATION, "TRUNCATE transactions CASCADE");
        assertRejected(RESTRICT_VIOLATION,
                "DELETE FROM transaction_status_changes WHERE transaction_id = '%s'".formatted(stored.id()));
        assertRejected(RESTRICT_VIOLATION,
                "UPDATE transaction_status_changes SET reason = 'X' WHERE transaction_id = '%s'".formatted(stored.id()));
    }

    // ---- Helpers -------------------------------------------------------------------

    private TransactionView completedDeposit(AccountView account) {
        return fixtures.submit(TransactionRequest.deposit(account.id(), hnl("10.00"), null), newKey())
                .transaction();
    }

    private static String deposit(String status, String amount) {
        return insert("DEPOSIT", status, amount, "NULL", "'%s'".formatted(UUID.randomUUID()));
    }

    private static String insert(String type, String status, String amount, String source,
            String destination) {
        return """
                INSERT INTO transactions (id, type, status, amount, currency, source_account_id,
                                          destination_account_id, idempotency_key, request_hash,
                                          created_by, created_at, updated_at)
                VALUES ('%s', '%s', '%s', %s, 'HNL', %s, %s, '%s', '%s', '%s', now(), now())"""
                .formatted(UUID.randomUUID(), type, status, amount, source, destination,
                        UUID.randomUUID(), "a".repeat(64), UUID.randomUUID());
    }

    private void assertRejected(String sqlState, String sql) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.execute(sql))
                        .isInstanceOf(SQLException.class)
                        .satisfies(ex -> assertThat(((SQLException) ex).getSQLState())
                                .as(sql).isEqualTo(sqlState));
            } finally {
                connection.rollback();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
