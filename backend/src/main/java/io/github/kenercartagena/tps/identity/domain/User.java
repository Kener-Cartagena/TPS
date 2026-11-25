package io.github.kenercartagena.tps.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * A person who operates TPS. The schema is defined by migration {@code V2}.
 *
 * <p>State changes go through the methods of this class, which keep the invariants
 * (normalized username, timestamps, lockout) in one place. The current time is always
 * passed in, never read here, so the rules can be tested with a fixed clock.
 *
 * <p>{@code @DynamicUpdate}: an update only writes the columns that changed. Failed
 * login counters are updated with atomic SQL statements ({@link UserRepository}); this
 * keeps an administrator's change (e.g. of the role) from overwriting them.
 */
@Entity
@Table(name = "users")
@DynamicUpdate
public class User {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(nullable = false)
    private int failedLoginAttempts;

    private @Nullable Instant lockedUntil;

    @Column(nullable = false)
    private Instant passwordChangedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    /** For JPA only. */
    protected User() {
    }

    private User(String username, String fullName, String passwordHash, Role role, Instant now) {
        this.username = Usernames.normalize(username);
        this.fullName = fullName.strip();
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.role = Objects.requireNonNull(role, "role");
        this.status = UserStatus.ACTIVE;
        this.failedLoginAttempts = 0;
        this.passwordChangedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** A new, active user. The password must already be hashed. */
    public static User create(String username, String fullName, String passwordHash, Role role,
            Instant now) {
        return new User(username, fullName, passwordHash, role, now);
    }

    // ---- Behavior ------------------------------------------------------------------

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /** Temporarily locked after too many failed logins, and the lockout has not expired. */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public void changeRole(Role newRole, Instant now) {
        this.role = Objects.requireNonNull(newRole, "role");
        this.updatedAt = now;
    }

    /** Allows the user to log in again. Also lifts a lockout, so an admin can unlock. */
    public void activate(Instant now) {
        this.status = UserStatus.ACTIVE;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.updatedAt = now;
    }

    public void disable(Instant now) {
        this.status = UserStatus.DISABLED;
        this.updatedAt = now;
    }

    /** Replaces the password. The new password must already be hashed. */
    public void changePassword(String newPasswordHash, Instant now) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "passwordHash");
        this.passwordChangedAt = now;
        this.updatedAt = now;
    }

    // ---- Accessors -----------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public @Nullable Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
