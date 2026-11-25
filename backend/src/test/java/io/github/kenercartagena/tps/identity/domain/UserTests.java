package io.github.kenercartagena.tps.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserTests {

    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");

    @Test
    void newUserIsActiveWithNormalizedUsernameAndTimestamps() {
        User user = User.create("  Jane.Doe ", "  Jane Doe ", "{bcrypt}hash", Role.OPERATOR, NOW);

        assertThat(user.getUsername()).isEqualTo("jane.doe");
        assertThat(user.getFullName()).isEqualTo("Jane Doe");
        assertThat(user.isActive()).isTrue();
        assertThat(user.isLocked(NOW)).isFalse();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
        assertThat(user.getUpdatedAt()).isEqualTo(NOW);
        assertThat(user.getPasswordChangedAt()).isEqualTo(NOW);
    }

    @Test
    void lockoutLastsUntilLockedUntil() {
        User user = lockedUntil(NOW.plus(Duration.ofMinutes(15)));

        assertThat(user.isLocked(NOW)).isTrue();
        assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void activatingLiftsTheLockout() {
        User user = lockedUntil(NOW.plus(Duration.ofMinutes(15)));
        ReflectionTestUtils.setField(user, "failedLoginAttempts", 3);

        user.activate(NOW);

        assertThat(user.isLocked(NOW)).isFalse();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void disablingKeepsTheUserButBlocksIt() {
        User user = User.create("jane", "Jane", "{bcrypt}hash", Role.OPERATOR, NOW);
        Instant later = NOW.plusSeconds(60);

        user.disable(later);

        assertThat(user.isActive()).isFalse();
        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(user.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    void changingThePasswordRecordsWhen() {
        User user = User.create("jane", "Jane", "{bcrypt}old", Role.OPERATOR, NOW);
        Instant later = NOW.plusSeconds(60);

        user.changePassword("{bcrypt}new", later);

        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}new");
        assertThat(user.getPasswordChangedAt()).isEqualTo(later);
    }

    /** Lockouts are written by an atomic SQL update, so tests set the field directly. */
    private static User lockedUntil(Instant lockedUntil) {
        User user = User.create("jane", "Jane", "{bcrypt}hash", Role.OPERATOR, NOW);
        ReflectionTestUtils.setField(user, "lockedUntil", lockedUntil);
        return user;
    }
}
