package io.github.kenercartagena.tps.shared.money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * An amount of money in one currency. Immutable.
 *
 * <p>Rules that keep money exact:
 * <ul>
 *   <li>Always {@link BigDecimal}, never {@code double}: {@code 0.1 + 0.2} must be {@code 0.3}.</li>
 *   <li>The scale is fixed to the currency's minor units ({@code 10} becomes {@code 10.00}).</li>
 *   <li>An amount with more decimals than the currency allows is <strong>rejected</strong>,
 *       never rounded: rounding would create or lose money silently.</li>
 *   <li>At most {@value #MAX_INTEGER_DIGITS} integer digits, matching the database
 *       columns ({@code NUMERIC(19,2)}).</li>
 *   <li>Operations between different currencies are rejected; there is no conversion.</li>
 * </ul>
 *
 * <p>Amounts may be negative (a balance can be negative on some accounts); whether a
 * negative amount is allowed is decided by the code that uses it.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    /** Integer digits allowed: {@code NUMERIC(19,2)} leaves 17 digits before the point. */
    public static final int MAX_INTEGER_DIGITS = 17;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.stripTrailingZeros().scale() > currency.minorUnits()) {
            throw new IllegalArgumentException("%s amounts have at most %d decimal places"
                    .formatted(currency, currency.minorUnits()));
        }
        amount = amount.setScale(currency.minorUnits());
        if (amount.precision() - amount.scale() > MAX_INTEGER_DIGITS) {
            throw new IllegalArgumentException(
                    "Amounts have at most %d integer digits".formatted(MAX_INTEGER_DIGITS));
        }
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    /** @param amount a decimal number such as {@code "1250.50"} */
    public static Money of(String amount, Currency currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    /** Compares two amounts of the same currency. */
    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    /** The amount as the API represents it: a plain decimal string, e.g. {@code "1250.50"}. */
    public String toPlainString() {
        return amount.toPlainString();
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }

    private void requireSameCurrency(Money other) {
        if (currency != other.currency) {
            throw new IllegalArgumentException(
                    "Currency mismatch: %s and %s".formatted(currency, other.currency));
        }
    }
}
