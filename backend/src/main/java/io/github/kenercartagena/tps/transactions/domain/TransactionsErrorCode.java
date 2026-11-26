package io.github.kenercartagena.tps.transactions.domain;

import io.github.kenercartagena.tps.shared.error.ErrorCode;

/**
 * Error codes of the transactions module. Part of the API contract: never rename them.
 * A rejected transaction also stores the code of the rule that refused it, which may come
 * from another module (e.g. {@code ACCOUNT_SUSPENDED} from accounts).
 */
public enum TransactionsErrorCode implements ErrorCode {

    /** 404: no transaction with the given id. */
    TRANSACTION_NOT_FOUND,
    /** 422: the idempotency key was already used for a different request. */
    IDEMPOTENCY_KEY_REUSED,
    /** 422: the transaction is in a currency different from its account's. */
    CURRENCY_MISMATCH,
    /** 422: the account does not have enough available funds. */
    INSUFFICIENT_FUNDS;

    @Override
    public String code() {
        return name();
    }
}
