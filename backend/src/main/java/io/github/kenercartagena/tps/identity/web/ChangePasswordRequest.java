package io.github.kenercartagena.tps.identity.web;

import io.github.kenercartagena.tps.identity.application.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /api/v1/users/me/password}. */
record ChangePasswordRequest(
        @NotBlank @Size(max = 128) String currentPassword,
        @NotNull @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH) String newPassword) {

    /** Never print passwords. */
    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=****, newPassword=****]";
    }
}
