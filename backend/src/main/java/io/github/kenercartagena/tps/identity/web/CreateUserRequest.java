package io.github.kenercartagena.tps.identity.web;

import io.github.kenercartagena.tps.identity.application.CreateUserCommand;
import io.github.kenercartagena.tps.identity.application.PasswordPolicy;
import io.github.kenercartagena.tps.identity.domain.Role;
import io.github.kenercartagena.tps.identity.domain.Usernames;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/users}.
 *
 * @param username 3 to 50 letters, digits, {@code .}, {@code _} or {@code -}; case-insensitive
 * @param password initial password, {@value PasswordPolicy#MIN_LENGTH} to
 *                 {@value PasswordPolicy#MAX_LENGTH} characters
 */
record CreateUserRequest(
        @NotBlank @Pattern(regexp = Usernames.PATTERN,
                message = "must be 3 to 50 letters, digits, '.', '_' or '-'") String username,
        @NotBlank @Size(max = 100) String fullName,
        @NotNull @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH) String password,
        @NotNull Role role) {

    CreateUserCommand toCommand() {
        return new CreateUserCommand(username, fullName, password, role);
    }

    /** Never print the password. */
    @Override
    public String toString() {
        return "CreateUserRequest[username=%s, fullName=%s, password=****, role=%s]"
                .formatted(username, fullName, role);
    }
}
