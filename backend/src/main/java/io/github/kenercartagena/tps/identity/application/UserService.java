package io.github.kenercartagena.tps.identity.application;

import io.github.kenercartagena.tps.identity.domain.Role;
import io.github.kenercartagena.tps.identity.domain.User;
import io.github.kenercartagena.tps.identity.domain.UserRepository;
import io.github.kenercartagena.tps.identity.domain.UserStatus;
import io.github.kenercartagena.tps.identity.domain.Usernames;
import io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException;
import io.github.kenercartagena.tps.shared.error.ConflictException;
import io.github.kenercartagena.tps.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User management use cases. Authorization (who may call what) is enforced by the web
 * layer with permissions; this class enforces the business rules that apply whoever the
 * caller is.
 *
 * <p>Security-relevant changes are logged with the ids of the actor and the target user
 * (never passwords or hashes). A persistent audit trail is added in the audit phase.
 */
@Service
@Transactional
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    UserService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    // ---- Queries -------------------------------------------------------------------

    @Transactional(readOnly = true)
    public UserView get(UUID userId) {
        return UserView.of(find(userId), clock.instant());
    }

    /** One page of users, sorted by username. */
    @Transactional(readOnly = true)
    public Page<UserView> list(int page, int size) {
        Instant now = clock.instant();
        return users.findAll(PageRequest.of(page, size, Sort.by("username")))
                .map(user -> UserView.of(user, now));
    }

    @Transactional(readOnly = true)
    public boolean hasAnyUser() {
        return users.count() > 0;
    }

    // ---- Commands ------------------------------------------------------------------

    /**
     * @param actorId the user performing the operation, or {@code null} for the system
     *                (bootstrap administrator)
     */
    public UserView create(CreateUserCommand command, @Nullable UUID actorId) {
        String username = Usernames.normalize(command.username());
        PasswordPolicy.check(command.password(), username);
        if (users.existsByUsername(username)) {
            throw usernameTaken(username, null);
        }

        Instant now = clock.instant();
        User user = User.create(username, command.fullName(),
                passwordEncoder.encode(command.password()), command.role(), now);
        try {
            // Flush now so a concurrent insert of the same username fails here (unique
            // constraint) and becomes a 409, instead of a 500 at commit time.
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw usernameTaken(username, ex);
        }

        log.info("User {} created by {} with role {}", user.getId(), actorOrSystem(actorId),
                user.getRole());
        return UserView.of(user, now);
    }

    public UserView changeRole(UUID userId, Role newRole, UUID actorId) {
        rejectSelfModification(userId, actorId);
        User user = find(userId);
        if (user.getRole() == newRole) {
            return UserView.of(user, clock.instant());
        }
        if (user.getRole() == Role.SYSTEM_ADMIN && user.isActive()) {
            ensureAnotherActiveAdminRemains(user);
        }

        Role previous = user.getRole();
        Instant now = clock.instant();
        user.changeRole(newRole, now);
        log.info("Role of user {} changed from {} to {} by {}", userId, previous, newRole, actorId);
        return UserView.of(user, now);
    }

    /** Activating also lifts a temporary lockout. */
    public UserView changeStatus(UUID userId, UserStatus newStatus, UUID actorId) {
        rejectSelfModification(userId, actorId);
        User user = find(userId);
        Instant now = clock.instant();

        switch (newStatus) {
            case ACTIVE -> user.activate(now);
            case DISABLED -> {
                if (user.getRole() == Role.SYSTEM_ADMIN && user.isActive()) {
                    ensureAnotherActiveAdminRemains(user);
                }
                user.disable(now);
            }
        }
        log.info("Status of user {} set to {} by {}", userId, newStatus, actorId);
        return UserView.of(user, now);
    }

    public void changeOwnPassword(UUID userId, String currentPassword, String newPassword) {
        User user = find(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessRuleViolationException(IdentityErrorCode.CURRENT_PASSWORD_INCORRECT,
                    "The current password is incorrect.");
        }
        if (currentPassword.equals(newPassword)) {
            throw new BusinessRuleViolationException(IdentityErrorCode.PASSWORD_POLICY_VIOLATION,
                    "The new password must be different from the current one.");
        }
        PasswordPolicy.check(newPassword, user.getUsername());

        user.changePassword(passwordEncoder.encode(newPassword), clock.instant());
        log.info("User {} changed their password", userId);
    }

    // ---- Rules ---------------------------------------------------------------------

    private User find(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new ResourceNotFoundException(
                IdentityErrorCode.USER_NOT_FOUND, "User '%s' was not found.".formatted(userId)));
    }

    /**
     * An administrator cannot lock themselves out by mistake, and changing one's own
     * access is exactly what segregation of duties forbids.
     */
    private static void rejectSelfModification(UUID userId, UUID actorId) {
        if (userId.equals(actorId)) {
            throw new BusinessRuleViolationException(IdentityErrorCode.SELF_MODIFICATION_NOT_ALLOWED,
                    "You cannot change your own role or status. Ask another administrator.");
        }
    }

    /**
     * Without an active administrator nobody could manage users anymore. The active
     * administrators are locked (SELECT ... FOR UPDATE), so two concurrent changes cannot
     * both pass this check and leave none.
     */
    private void ensureAnotherActiveAdminRemains(User admin) {
        boolean anotherRemains = users.lockAllByRoleAndStatus(Role.SYSTEM_ADMIN, UserStatus.ACTIVE)
                .stream()
                .anyMatch(other -> !other.getId().equals(admin.getId()));
        if (!anotherRemains) {
            throw new BusinessRuleViolationException(IdentityErrorCode.LAST_ACTIVE_ADMIN,
                    "The system must keep at least one active administrator.");
        }
    }

    private static ConflictException usernameTaken(String username, @Nullable Throwable cause) {
        String message = "The username '%s' is already taken.".formatted(username);
        return cause == null
                ? new ConflictException(IdentityErrorCode.USERNAME_ALREADY_EXISTS, message)
                : new ConflictException(IdentityErrorCode.USERNAME_ALREADY_EXISTS, message, cause);
    }

    private static Object actorOrSystem(@Nullable UUID actorId) {
        return actorId != null ? actorId : "system";
    }
}
