package io.github.kenercartagena.tps.accounts.application;

import io.github.kenercartagena.tps.shared.money.Currency;
import java.time.Instant;
import java.util.UUID;

/**
 * Balances of an account, as decimal strings.
 *
 * @param ledgerBalance    the sum of every posted movement
 * @param availableBalance what the holder can use: the ledger balance minus funds on hold.
 *                         Holds arrive with pending operations (Phases 4 and 6); until
 *                         then both balances are equal
 * @param asOf             when the balances were read
 */
public record AccountBalanceView(
        UUID accountId,
        String accountNumber,
        Currency currency,
        String ledgerBalance,
        String availableBalance,
        Instant asOf) {
}
