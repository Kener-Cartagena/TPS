package io.github.kenercartagena.tps.shared.money;

/**
 * Currencies TPS operates in, identified by their ISO 4217 code.
 *
 * <p>The list is deliberately closed: adding a currency is a product decision (and needs
 * a migration, because the database only accepts these codes). Both currencies have two
 * minor units (centavos and cents).
 */
public enum Currency {

    /** Honduran lempira. */
    HNL(2),
    /** United States dollar. */
    USD(2);

    private final int minorUnits;

    Currency(int minorUnits) {
        this.minorUnits = minorUnits;
    }

    /** Number of decimal places of an amount in this currency. */
    public int minorUnits() {
        return minorUnits;
    }
}
