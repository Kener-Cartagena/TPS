package io.github.kenercartagena.tps.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Exact money: fixed scale, no rounding, no mixing of currencies. */
class MoneyTests {

    @Test
    void scaleIsFixedToTheCurrencyMinorUnits() {
        assertThat(Money.of("10", Currency.HNL).amount()).isEqualTo(new BigDecimal("10.00"));
        assertThat(Money.of("10.5", Currency.HNL).toPlainString()).isEqualTo("10.50");
        assertThat(Money.of("10.500", Currency.USD).toPlainString()).isEqualTo("10.50");
    }

    @Test
    void equalAmountsAreEqualWhateverTheirInputScale() {
        assertThat(Money.of("10", Currency.HNL)).isEqualTo(Money.of("10.00", Currency.HNL));
        assertThat(Money.of("10", Currency.HNL)).isNotEqualTo(Money.of("10", Currency.USD));
    }

    /** Rounding would create or lose money silently. */
    @Test
    void moreDecimalsThanTheCurrencyAllowsAreRejectedNotRounded() {
        assertThatThrownBy(() -> Money.of("10.005", Currency.HNL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 2 decimal places");
    }

    @Test
    void amountsAreLimitedToSeventeenIntegerDigits() {
        assertThat(Money.of("99999999999999999.99", Currency.HNL).isPositive()).isTrue();
        assertThatThrownBy(() -> Money.of("100000000000000000", Currency.HNL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void arithmeticIsExact() {
        Money sum = Money.of("0.10", Currency.USD).plus(Money.of("0.20", Currency.USD));

        assertThat(sum).isEqualTo(Money.of("0.30", Currency.USD));
        assertThat(sum.minus(Money.of("0.50", Currency.USD))).isEqualTo(Money.of("-0.20", Currency.USD));
        assertThat(Money.of("5", Currency.USD).negate()).isEqualTo(Money.of("-5", Currency.USD));
    }

    @Test
    void currenciesAreNeverMixed() {
        Money lempiras = Money.of("1", Currency.HNL);
        Money dollars = Money.of("1", Currency.USD);

        assertThatThrownBy(() -> lempiras.plus(dollars)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lempiras.minus(dollars)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lempiras.compareTo(dollars)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void signIsReported() {
        assertThat(Money.of("0.01", Currency.HNL).isPositive()).isTrue();
        assertThat(Money.of("-0.01", Currency.HNL).isNegative()).isTrue();
        assertThat(Money.zero(Currency.HNL).isZero()).isTrue();
        assertThat(Money.of("2", Currency.HNL)).isGreaterThan(Money.of("1.99", Currency.HNL));
    }

    @Test
    void plainStringNeverUsesScientificNotation() {
        assertThat(Money.of(new BigDecimal("1E+3"), Currency.HNL).toPlainString()).isEqualTo("1000.00");
        assertThat(Money.of("1250.5", Currency.HNL)).hasToString("1250.50 HNL");
    }
}
