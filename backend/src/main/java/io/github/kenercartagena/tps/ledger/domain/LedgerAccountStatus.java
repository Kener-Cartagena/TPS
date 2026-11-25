package io.github.kenercartagena.tps.ledger.domain;

/** Whether a ledger account accepts postings. Closing is final. */
public enum LedgerAccountStatus {
    OPEN,
    CLOSED
}
