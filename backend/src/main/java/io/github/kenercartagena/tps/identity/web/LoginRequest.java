package io.github.kenercartagena.tps.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credentials for {@code POST /api/v1/auth/tokens}. Only the size is validated here (to
 * bound the hashing work); format rules would hint at which usernames can exist.
 */
record LoginRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(max = 128) String password) {

    /** Never print the password. */
    @Override
    public String toString() {
        return "LoginRequest[username=%s, password=****]".formatted(username);
    }
}
