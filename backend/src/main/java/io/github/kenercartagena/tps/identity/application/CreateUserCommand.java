package io.github.kenercartagena.tps.identity.application;

import io.github.kenercartagena.tps.identity.domain.Role;

/**
 * Input to create a user.
 *
 * @param password the initial password in plain text; hashed before it is stored
 */
public record CreateUserCommand(String username, String fullName, String password, Role role) {

    /** Never print the password (logs, debugger, exception messages). */
    @Override
    public String toString() {
        return "CreateUserCommand[username=%s, fullName=%s, role=%s]"
                .formatted(username, fullName, role);
    }
}
