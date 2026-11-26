package io.github.kenercartagena.tps.transactions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kenercartagena.tps.shared.money.Currency;
import io.github.kenercartagena.tps.shared.money.Money;
import io.github.kenercartagena.tps.transactions.application.TransactionRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Idempotency keys and request fingerprints: what counts as "the same request". */
class IdempotencyTests {

    private static final UUID ACCOUNT = UUID.randomUUID();

    @ParameterizedTest
    @ValueSource(strings = {"8e03978e-40d5-43e8-bc93-6894a57f9324", "a", "order:2026.11.25_001"})
    void validKeysAreAccepted(String value) {
        assertThat(new IdempotencyKey(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "with space", "\"quoted\"", "ñ", "semi;colon"})
    void invalidKeysAreRejected(String value) {
        assertThatThrownBy(() -> new IdempotencyKey(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keysHaveAtMost64Characters() {
        assertThat(new IdempotencyKey("k".repeat(64)).value()).hasSize(64);
        assertThatThrownBy(() -> new IdempotencyKey("k".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aFingerprintIsTheSha256OfItsLengthPrefixedParts() {
        // printf '1:a' | sha256sum           (part "a": length 1)
        assertThat(RequestFingerprint.of("a").value())
                .isEqualTo("4162fddd39a3e4225e8e2392eced237fbeb34e6e218b5647d27bd4d2b9c0da24");
        // printf '7:DEPOSIT-' | sha256sum    (enum as its name, then null as '-')
        assertThat(RequestFingerprint.of(TransactionType.DEPOSIT, null).value())
                .isEqualTo("ca915798c3f7a7e46f916eebc457d27841f0eaa1fed78933a2762cb10c2c4bbc");
    }

    @Test
    void partsAreUnambiguous() {
        assertThat(RequestFingerprint.of("ab", "c")).isNotEqualTo(RequestFingerprint.of("a", "bc"));
        assertThat(RequestFingerprint.of("a", null)).isNotEqualTo(RequestFingerprint.of("a", "-"));
        assertThat(RequestFingerprint.of((Object) null)).isNotEqualTo(RequestFingerprint.of(""));
    }

    @Test
    void theSameOperationHasTheSameFingerprintHoweverItIsWritten() {
        TransactionRequest first = TransactionRequest.deposit(ACCOUNT, Money.of("10", Currency.HNL), "Rent");
        TransactionRequest retry = TransactionRequest.deposit(ACCOUNT, Money.of("10.00", Currency.HNL), " Rent ");

        assertThat(retry.fingerprint()).isEqualTo(first.fingerprint());
    }

    @Test
    void anyChangeInTheOperationChangesTheFingerprint() {
        TransactionRequest base = TransactionRequest.deposit(ACCOUNT, Money.of("10.00", Currency.HNL), null);

        assertThat(TransactionRequest.deposit(ACCOUNT, Money.of("10.01", Currency.HNL), null).fingerprint())
                .isNotEqualTo(base.fingerprint());
        assertThat(TransactionRequest.deposit(ACCOUNT, Money.of("10.00", Currency.USD), null).fingerprint())
                .isNotEqualTo(base.fingerprint());
        assertThat(TransactionRequest.deposit(UUID.randomUUID(), Money.of("10.00", Currency.HNL), null)
                .fingerprint()).isNotEqualTo(base.fingerprint());
        assertThat(TransactionRequest.deposit(ACCOUNT, Money.of("10.00", Currency.HNL), "Note").fingerprint())
                .isNotEqualTo(base.fingerprint());
        assertThat(TransactionRequest.withdrawal(ACCOUNT, Money.of("10.00", Currency.HNL), null).fingerprint())
                .isNotEqualTo(base.fingerprint());
    }
}
