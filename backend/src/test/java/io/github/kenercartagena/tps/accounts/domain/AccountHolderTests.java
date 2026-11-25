package io.github.kenercartagena.tps.accounts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Identity documents accepted for account holders in Honduras. */
class AccountHolderTests {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
            "DNI,      0801-1990-12345,     true",
            "DNI,      0801199012345,       true",
            "DNI,      '0801 1990 12345',   true",
            "DNI,      080119901234,        false",
            "DNI,      08011990123456,      false",
            "DNI,      0801-1990-1234A,     false",
            "RTN,      0801-1990-123456,    true",
            "RTN,      0801199012345,       false",
            "PASSPORT, e1234567,            true",
            "PASSPORT, AB12,                false",
            "PASSPORT, AB12*34567,          false"})
    void documentFormats(DocumentType type, String number, boolean valid) {
        assertThat(type.isValid(number)).isEqualTo(valid);
    }

    @Test
    void documentNumberIsStoredNormalized() {
        AccountHolder holder = AccountHolder.register(HolderType.PERSON, "  María López  ",
                DocumentType.DNI, "0801-1990-12345", null, NOW);

        assertThat(holder.getDocumentNumber()).isEqualTo("0801199012345");
        assertThat(holder.getFullName()).isEqualTo("María López");
        assertThat(holder.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void passportIsStoredInUppercase() {
        AccountHolder holder = AccountHolder.register(HolderType.PERSON, "John Doe",
                DocumentType.PASSPORT, "e1234567", null, NOW);

        assertThat(holder.getDocumentNumber()).isEqualTo("E1234567");
    }

    @Test
    void businessMustUseItsRtn() {
        assertThat(HolderType.BUSINESS.accepts(DocumentType.RTN)).isTrue();
        assertThat(HolderType.BUSINESS.accepts(DocumentType.DNI)).isFalse();
        assertThat(HolderType.BUSINESS.accepts(DocumentType.PASSPORT)).isFalse();

        assertThatThrownBy(() -> AccountHolder.register(HolderType.BUSINESS, "ACME S.A.",
                DocumentType.DNI, "0801199012345", null, NOW))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting(ex -> ((BusinessRuleViolationException) ex).getErrorCode())
                .isEqualTo(AccountsErrorCode.INVALID_HOLDER_DOCUMENT);
    }

    @Test
    void personMayUseAnyDocument() {
        for (DocumentType type : DocumentType.values()) {
            assertThat(HolderType.PERSON.accepts(type)).as(type.name()).isTrue();
        }
    }

    @Test
    void invalidNumberIsRejected() {
        assertThatThrownBy(() -> AccountHolder.register(HolderType.PERSON, "María López",
                DocumentType.DNI, "123", null, NOW))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("The DNI number must be 13 digits (dashes allowed).");
    }
}
