package io.github.kenercartagena.tps.ledger;

import io.github.kenercartagena.tps.shared.money.Currency;
import java.util.UUID;

/**
 * Internal accounts of the ledger, created by migration {@code V4} with fixed ids.
 *
 * <p>A <em>settlement account</em> (type {@link LedgerAccountType#ASSET}) represents the
 * money TPS holds at its financial institutions, one per currency. Money enters and
 * leaves customer accounts through it.
 */
public final class SystemLedgerAccounts {

    public static final UUID SETTLEMENT_HNL = UUID.fromString("00000000-0000-7000-8000-000000000101");
    public static final UUID SETTLEMENT_USD = UUID.fromString("00000000-0000-7000-8000-000000000102");

    private SystemLedgerAccounts() {
    }

    /** The settlement account of a currency. */
    public static UUID settlement(Currency currency) {
        return switch (currency) {
            case HNL -> SETTLEMENT_HNL;
            case USD -> SETTLEMENT_USD;
        };
    }
}
