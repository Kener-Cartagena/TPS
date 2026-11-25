package io.github.kenercartagena.tps.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kenercartagena.tps.TestcontainersConfiguration;
import io.github.kenercartagena.tps.shared.money.Currency;
import io.github.kenercartagena.tps.shared.money.Money;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * The ledger's rules hold even for SQL that bypasses the application: each test writes
 * directly with JDBC and expects PostgreSQL itself to refuse (migration {@code V3}).
 *
 * <p>Every test runs in its own JDBC transaction that is always rolled back, so nothing
 * written here reaches the shared database. Assertions check the SQLSTATE, the standard
 * error code: {@code 23514} check violation, {@code 23001} restrict violation (immutable
 * row), {@code 23503} foreign key violation.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LedgerDatabaseConstraintsTests {

    private static final String CHECK_VIOLATION = "23514";
    private static final String RESTRICT_VIOLATION = "23001";
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Ledger ledger;

    private UUID bank;
    private UUID customer;
    private UUID dollars;
    private UUID postedEntry;

    /** Real accounts and one real entry, created through the application. */
    @BeforeEach
    void createAccountsAndAnEntry() {
        bank = open(LedgerAccountType.ASSET, Currency.HNL);
        customer = open(LedgerAccountType.LIABILITY, Currency.HNL);
        dollars = open(LedgerAccountType.LIABILITY, Currency.USD);
        postedEntry = ledger.post(new JournalEntryRequest("Deposit", null, null, List.of(
                JournalLine.debit(bank, Money.of("10.00", Currency.HNL)),
                JournalLine.credit(customer, Money.of("10.00", Currency.HNL))))).journalEntryId();
    }

    // ---- Double entry (deferred to COMMIT) ------------------------------------------

    @Test
    void unbalancedEntryCannotBeCommitted() {
        inRolledBackTransaction(connection -> {
            UUID entry = insertEntry(connection);
            insertPosting(connection, entry, 1, bank, "HNL", "DEBIT", "10.00");
            insertPosting(connection, entry, 2, customer, "HNL", "CREDIT", "9.99");

            assertSqlState(connection::commit, CHECK_VIOLATION);
        });
    }

    @Test
    void entryWithoutPostingsCannotBeCommitted() {
        inRolledBackTransaction(connection -> {
            insertEntry(connection);

            assertSqlState(connection::commit, CHECK_VIOLATION);
        });
    }

    @Test
    void entryWithOneLineCannotBeCommitted() {
        inRolledBackTransaction(connection -> {
            UUID entry = insertEntry(connection);
            insertPosting(connection, entry, 1, bank, "HNL", "DEBIT", "10.00");

            assertSqlState(connection::commit, CHECK_VIOLATION);
        });
    }

    @Test
    void entryMixingCurrenciesCannotBeCommitted() {
        inRolledBackTransaction(connection -> {
            UUID entry = insertEntry(connection);
            insertPosting(connection, entry, 1, bank, "HNL", "DEBIT", "10.00");
            insertPosting(connection, entry, 2, dollars, "USD", "CREDIT", "10.00");

            assertSqlState(connection::commit, CHECK_VIOLATION);
        });
    }

    /** The check runs at commit; forcing it earlier shows that a balanced entry passes. */
    @Test
    void balancedEntryPassesTheCheck() {
        inRolledBackTransaction(connection -> {
            UUID entry = insertEntry(connection);
            insertPosting(connection, entry, 1, bank, "HNL", "DEBIT", "10.00");
            insertPosting(connection, entry, 2, customer, "HNL", "CREDIT", "10.00");

            assertThatCode(() -> execute(connection, "SET CONSTRAINTS ALL IMMEDIATE"))
                    .doesNotThrowAnyException();
        });
    }

    // ---- Row rules ------------------------------------------------------------------

    @Test
    void postingMustUseTheCurrencyOfItsAccount() {
        inRolledBackTransaction(connection -> {
            UUID entry = insertEntry(connection);

            assertSqlState(() -> insertPosting(connection, entry, 1, dollars, "HNL", "DEBIT", "1.00"),
                    FOREIGN_KEY_VIOLATION);
        });
    }

    @Test
    void postingAmountMustBePositive() {
        inRolledBackTransaction(connection -> {
            UUID entry = insertEntry(connection);

            assertSqlState(() -> insertPosting(connection, entry, 1, bank, "HNL", "DEBIT", "0.00"),
                    CHECK_VIOLATION);
        });
    }

    @Test
    void accountThatForbidsItCannotHaveANegativeBalance() {
        inRolledBackTransaction(connection -> {
            assertSqlState(() -> execute(connection,
                    "UPDATE ledger_accounts SET balance = -0.01 WHERE id = '" + customer + "'"),
                    CHECK_VIOLATION);
        });
    }

    @Test
    void accountWithABalanceCannotBeClosed() {
        inRolledBackTransaction(connection -> {
            assertSqlState(() -> execute(connection, "UPDATE ledger_accounts"
                    + " SET status = 'CLOSED', closed_at = now() WHERE id = '" + customer + "'"),
                    CHECK_VIOLATION);
        });
    }

    // ---- Immutability ---------------------------------------------------------------

    @Test
    void postingsCannotBeChangedOrDeleted() {
        String ofEntry = " WHERE journal_entry_id = '" + postedEntry + "'";
        inRolledBackTransaction(connection -> {
            assertSqlState(() -> execute(connection, "UPDATE ledger_postings SET amount = 1" + ofEntry),
                    RESTRICT_VIOLATION);
        });
        inRolledBackTransaction(connection -> {
            assertSqlState(() -> execute(connection, "DELETE FROM ledger_postings" + ofEntry),
                    RESTRICT_VIOLATION);
        });
        inRolledBackTransaction(connection -> {
            assertSqlState(() -> execute(connection, "TRUNCATE ledger_postings"),
                    RESTRICT_VIOLATION);
        });
    }

    @Test
    void journalEntriesCannotBeChangedOrDeleted() {
        String byId = " WHERE id = '" + postedEntry + "'";
        inRolledBackTransaction(connection -> {
            assertSqlState(() -> execute(connection,
                    "UPDATE journal_entries SET description = 'Edited'" + byId), RESTRICT_VIOLATION);
        });
        inRolledBackTransaction(connection -> {
            assertSqlState(() -> execute(connection, "DELETE FROM journal_entries" + byId),
                    RESTRICT_VIOLATION);
        });
    }

    @Test
    void ledgerAccountsCannotBeDeleted() {
        inRolledBackTransaction(connection -> {
            assertSqlState(() -> execute(connection,
                    "DELETE FROM ledger_accounts WHERE id = '" + dollars + "'"), RESTRICT_VIOLATION);
        });
    }

    // ---- Helpers --------------------------------------------------------------------

    private UUID open(LedgerAccountType type, Currency currency) {
        return ledger.openAccount(new NewLedgerAccount(LedgerIntegrationTests.uniqueCode(),
                "Constraint test", type, currency, false));
    }

    /**
     * Runs the work in a JDBC transaction (manual commit) that is always rolled back,
     * whatever happens, so nothing reaches the shared database.
     */
    private void inRolledBackTransaction(SqlWork work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                work.run(connection);
            } finally {
                connection.rollback();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unexpected database error in test setup", ex);
        }
    }

    private static UUID insertEntry(Connection connection) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO journal_entries (id, description, posted_at) VALUES (?, 'Raw SQL', now())")) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }
        return id;
    }

    private static void insertPosting(Connection connection, UUID entry, int line, UUID account,
            String currency, String direction, String amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ledger_postings (id, journal_entry_id, line_number, ledger_account_id,
                                             currency, direction, amount, balance_after, posted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::numeric, 0, now())""")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, entry);
            statement.setInt(3, line);
            statement.setObject(4, account);
            statement.setString(5, currency);
            statement.setString(6, direction);
            statement.setString(7, amount);
            statement.executeUpdate();
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void assertSqlState(SqlAction action, String sqlState) {
        assertThatThrownBy(action::run)
                .isInstanceOf(SQLException.class)
                .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo(sqlState));
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }

    @FunctionalInterface
    private interface SqlWork {
        void run(Connection connection) throws SQLException;
    }
}
