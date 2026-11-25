package io.github.kenercartagena.tps.ledger;

/** Side of a posting. Whether it increases or decreases a balance depends on the account type. */
public enum EntryDirection {
    DEBIT,
    CREDIT
}
