package io.github.kenercartagena.tps;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Smoke tests for the application foundation: the Spring context starts against a
 * real PostgreSQL database and every Flyway migration is applied successfully.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TpsApplicationTests {

    @Autowired
    private Flyway flyway;

    @Test
    void contextLoads() {
        // Fails if the application cannot start or connect to the database.
    }

    @Test
    void allMigrationsAreApplied() {
        MigrationInfo[] pending = flyway.info().pending();
        MigrationInfo current = flyway.info().current();

        assertThat(pending).as("pending migrations").isEmpty();
        assertThat(current).as("current migration").isNotNull();
        assertThat(current.getState().isFailed()).as("current migration failed").isFalse();
    }
}
