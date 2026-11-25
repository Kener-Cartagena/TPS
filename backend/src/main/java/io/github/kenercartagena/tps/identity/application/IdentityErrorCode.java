package io.github.kenercartagena.tps.identity.application;

import io.github.kenercartagena.tps.shared.error.ErrorCode;

/** Error codes of the identity module. Part of the API contract: never rename them. */
public enum IdentityErrorCode implements ErrorCode {

    /** 404: no user with the given id. */
    USER_NOT_FOUND,
    /** 409: the (normalized) username is already taken. */
    USERNAME_ALREADY_EXISTS,
    /** 401: login failed. Deliberately does not say whether the user exists, is locked or disabled. */
    INVALID_CREDENTIALS,
    /** 422: the current password given to change the password is wrong. */
    CURRENT_PASSWORD_INCORRECT,
    /** 422: the new password breaks the password policy. */
    PASSWORD_POLICY_VIOLATION,
    /** 422: an administrator tried to change their own role or status. */
    SELF_MODIFICATION_NOT_ALLOWED,
    /** 422: the change would leave the system without an active administrator. */
    LAST_ACTIVE_ADMIN;

    @Override
    public String code() {
        return name();
    }
}
