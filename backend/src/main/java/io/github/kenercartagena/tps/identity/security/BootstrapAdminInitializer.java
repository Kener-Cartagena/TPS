package io.github.kenercartagena.tps.identity.security;

import io.github.kenercartagena.tps.identity.application.CreateUserCommand;
import io.github.kenercartagena.tps.identity.application.UserService;
import io.github.kenercartagena.tps.identity.domain.Role;
import io.github.kenercartagena.tps.shared.error.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator on startup, so a fresh installation can be used
 * without inserting users by hand.
 *
 * <p>It only acts when the database has <strong>no users at all</strong> and
 * {@code tps.security.bootstrap.admin.username/password} are set (in production through
 * {@code TPS_SECURITY_BOOTSTRAP_ADMIN_*} environment variables). Afterwards the variables
 * are ignored and can be removed. A known password is never stored in a migration,
 * because the repository is public.
 */
@Component
class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);
    private static final String DEFAULT_FULL_NAME = "System Administrator";

    private final UserService userService;
    private final SecurityProperties properties;

    BootstrapAdminInitializer(UserService userService, SecurityProperties properties) {
        this.userService = userService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userService.hasAnyUser()) {
            return;
        }
        SecurityProperties.Admin admin = properties.bootstrap() != null
                ? properties.bootstrap().admin() : null;
        if (admin == null || !admin.isConfigured()) {
            log.warn("No users exist and no bootstrap administrator is configured "
                    + "(tps.security.bootstrap.admin.*): nobody can log in.");
            return;
        }

        String fullName = admin.fullName() != null && !admin.fullName().isBlank()
                ? admin.fullName() : DEFAULT_FULL_NAME;
        try {
            userService.create(
                    new CreateUserCommand(admin.username(), fullName, admin.password(), Role.SYSTEM_ADMIN),
                    null);
            log.info("Bootstrap administrator '{}' created. Change its password after the first login.",
                    admin.username());
        } catch (ConflictException ex) {
            // Another instance started at the same time and created it first.
            log.info("Bootstrap administrator already exists");
        }
    }
}
