package io.github.kenercartagena.tps.accounts.domain;

import io.github.kenercartagena.tps.shared.error.ErrorCode;

/** Error codes of the accounts module. Part of the API contract: never rename them. */
public enum AccountsErrorCode implements ErrorCode {

    /** 404: no account holder with the given id. */
    ACCOUNT_HOLDER_NOT_FOUND,
    /** 409: a holder with the same identity document is already registered. */
    ACCOUNT_HOLDER_ALREADY_EXISTS,
    /** 422: the identity document is not valid for its type or for the holder type. */
    INVALID_HOLDER_DOCUMENT,
    /** 404: no account with the given id. */
    ACCOUNT_NOT_FOUND,
    /** 422: the account cannot change from its current status to the requested one. */
    INVALID_STATUS_TRANSITION,
    /** 422: only an account with a zero balance can be closed. */
    ACCOUNT_HAS_BALANCE;

    @Override
    public String code() {
        return name();
    }
}
