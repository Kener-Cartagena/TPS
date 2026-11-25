package io.github.kenercartagena.tps.ledger;

/**
 * Accounting type of a ledger account. The type decides its <em>normal side</em>: the
 * direction that increases its balance.
 *
 * <table>
 *   <caption>Normal side per type</caption>
 *   <tr><th>Type</th><th>Increased by</th><th>Example in TPS</th></tr>
 *   <tr><td>ASSET</td><td>DEBIT</td><td>Settlement account: money TPS holds at its bank</td></tr>
 *   <tr><td>LIABILITY</td><td>CREDIT</td><td>Customer account: money TPS owes its customer</td></tr>
 *   <tr><td>EQUITY</td><td>CREDIT</td><td>Capital</td></tr>
 *   <tr><td>REVENUE</td><td>CREDIT</td><td>Fees earned</td></tr>
 *   <tr><td>EXPENSE</td><td>DEBIT</td><td>Provider costs</td></tr>
 * </table>
 */
public enum LedgerAccountType {

    ASSET(EntryDirection.DEBIT),
    LIABILITY(EntryDirection.CREDIT),
    EQUITY(EntryDirection.CREDIT),
    REVENUE(EntryDirection.CREDIT),
    EXPENSE(EntryDirection.DEBIT);

    private final EntryDirection normalSide;

    LedgerAccountType(EntryDirection normalSide) {
        this.normalSide = normalSide;
    }

    /** The direction that increases the balance of accounts of this type. */
    public EntryDirection normalSide() {
        return normalSide;
    }
}
