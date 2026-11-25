package io.github.kenercartagena.tps.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kenercartagena.tps.TestcontainersConfiguration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Account rules that PostgreSQL enforces by itself (migration {@code V5}), checked with SQL
 * that bypasses the application. Each statement runs in a transaction that is rolled back.
 * CHECK constraints are evaluated before foreign keys, so the referenced rows need not
 * exist: the CHECK under test is what rejects the row ({@code 23514}).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountsDatabaseConstraintsTests {

    private static final String CHECK_VIOLATION = "23514";

    @Autowired
    private DataSource dataSource;

    @Test
    void accountNumberMustHaveValidCheckDigits() {
        assertRejected(insertAccount("100000000094"));
        assertRejected(insertAccount("10000000009"));
    }

    @Test
    void documentNumbersFollowTheirFormat() {
        assertRejected(insertHolder("PERSON", "DNI", "080119901234"));
        assertRejected(insertHolder("PERSON", "RTN", "0801199012345"));
        assertRejected(insertHolder("PERSON", "DNI", "0801-1990-12345"));
        assertRejected(insertHolder("PERSON", "PASSPORT", "e1234567"));
    }

    @Test
    void businessMustBeIdentifiedByItsRtn() {
        assertRejected(insertHolder("BUSINESS", "DNI", "0801199012345"));
    }

    private static String insertAccount(String accountNumber) {
        return """
                INSERT INTO accounts (id, account_number, holder_id, type, currency, status,
                                      ledger_account_id, opened_at, updated_at)
                VALUES ('%s', '%s', '%s', 'SAVINGS', 'HNL', 'ACTIVE', '%s', now(), now())"""
                .formatted(UUID.randomUUID(), accountNumber, UUID.randomUUID(), UUID.randomUUID());
    }

    private static String insertHolder(String holderType, String documentType, String number) {
        return """
                INSERT INTO account_holders (id, holder_type, full_name, document_type,
                                             document_number, created_at, updated_at)
                VALUES ('%s', '%s', 'Raw SQL', '%s', '%s', now(), now())"""
                .formatted(UUID.randomUUID(), holderType, documentType, number);
    }

    private void assertRejected(String sql) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.execute(sql))
                        .isInstanceOf(SQLException.class)
                        .satisfies(ex -> assertThat(((SQLException) ex).getSQLState())
                                .as(sql).isEqualTo(CHECK_VIOLATION));
            } finally {
                connection.rollback();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
