package io.github.kenercartagena.tps.accounts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Account numbers: 10-digit sequence + 2 ISO 7064 MOD 97-10 check digits. */
class AccountNumberTests {

    @Test
    void checkDigitsAreAppendedToTheSequence() {
        assertThat(AccountNumber.fromSequence(1_000_000_000L).value()).isEqualTo("100000000093");
    }

    @ParameterizedTest
    @ValueSource(longs = {1_000_000_000L, 1_000_000_001L, 4_242_424_242L, 9_999_999_999L})
    void everyGeneratedNumberIsValid(long sequence) {
        String number = AccountNumber.fromSequence(sequence).value();

        assertThat(number).hasSize(12).startsWith(Long.toString(sequence));
        assertThat(Long.parseLong(number) % 97).isEqualTo(1);
        assertThat(AccountNumber.isValid(number)).isTrue();
    }

    /** The point of check digits: a mistyped digit never reaches another account. */
    @Test
    void everySingleMistypedDigitIsDetected() {
        String number = AccountNumber.fromSequence(4_242_424_242L).value();

        for (int position = 0; position < number.length(); position++) {
            for (char digit = '0'; digit <= '9'; digit++) {
                if (digit == number.charAt(position)) {
                    continue;
                }
                String typo = number.substring(0, position) + digit + number.substring(position + 1);
                assertThat(AccountNumber.isValid(typo)).as("typo %s", typo).isFalse();
            }
        }
    }

    @Test
    void swappedNeighbourDigitsAreDetected() {
        String number = AccountNumber.fromSequence(1_234_567_890L).value();

        for (int position = 0; position < number.length() - 1; position++) {
            if (number.charAt(position) == number.charAt(position + 1)) {
                continue;
            }
            String swapped = number.substring(0, position) + number.charAt(position + 1)
                    + number.charAt(position) + number.substring(position + 2);
            assertThat(AccountNumber.isValid(swapped)).as("swap %s", swapped).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "10000000009", "1000000000930", "10000000009A", "100000000094"})
    void malformedNumbersAreInvalid(String candidate) {
        assertThat(AccountNumber.isValid(candidate)).isFalse();
        assertThatThrownBy(() -> new AccountNumber(candidate)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sequenceMustHaveTenDigits() {
        assertThatThrownBy(() -> AccountNumber.fromSequence(999_999_999L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccountNumber.fromSequence(10_000_000_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
