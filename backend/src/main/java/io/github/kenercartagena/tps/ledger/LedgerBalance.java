package io.github.kenercartagena.tps.ledger;

import io.github.kenercartagena.tps.shared.money.Money;
import java.util.UUID;

/**
 * Current balance of a ledger account, on its normal side (see {@link LedgerAccountType}):
 * for a customer account, a positive balance is money TPS owes the customer.
 */
public record LedgerBalance(UUID ledgerAccountId, Money balance) {
}
