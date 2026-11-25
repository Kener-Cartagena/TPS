package io.github.kenercartagena.tps.ledger.domain;

import io.github.kenercartagena.tps.shared.error.ErrorCode;

/** Error codes of the ledger module. Part of the API contract: never rename them. */
public enum LedgerErrorCode implements ErrorCode {

    /** 404: no ledger account with the given id. */
    LEDGER_ACCOUNT_NOT_FOUND,
    /** 404: no journal entry with the given id. */
    JOURNAL_ENTRY_NOT_FOUND,
    /** 409: another ledger account already has this code. */
    LEDGER_ACCOUNT_CODE_ALREADY_EXISTS,
    /** 422: fewer than two lines, a non-positive amount or more than one currency. */
    INVALID_JOURNAL_ENTRY,
    /** 422: the debits of the entry do not add up to its credits. */
    UNBALANCED_JOURNAL_ENTRY,
    /** 422: a line uses a currency different from its account's. */
    CURRENCY_MISMATCH,
    /** 422: a line posts to a closed account. */
    LEDGER_ACCOUNT_CLOSED,
    /** 422: the entry would leave an account that does not allow it with a negative balance. */
    INSUFFICIENT_FUNDS;

    @Override
    public String code() {
        return name();
    }
}
