package io.github.kenercartagena.tps.identity.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTests {

    @ParameterizedTest
    @ValueSource(strings = {
            "twelve chars",                  // exactly 12 characters, spaces allowed
            "correct horse battery staple"   // a passphrase: no "complexity" rules needed
    })
    void acceptsPasswordsWithinTheLengthLimits(String password) {
        assertThatCode(() -> PasswordPolicy.check(password, "jane")).doesNotThrowAnyException();
    }

    @Test
    void acceptsTheMaximumLength() {
        assertThatCode(() -> PasswordPolicy.check("a".repeat(PasswordPolicy.MAX_LENGTH), "jane"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsPasswordsShorterThanTheMinimum() {
        assertViolation("a".repeat(PasswordPolicy.MIN_LENGTH - 1), "jane");
    }

    @Test
    void rejectsPasswordsLongerThanTheMaximum() {
        assertViolation("a".repeat(PasswordPolicy.MAX_LENGTH + 1), "jane");
    }

    /** 20 emoji = 20 characters but 80 bytes: bcrypt would silently ignore the rest. */
    @Test
    void rejectsPasswordsLongerThan72BytesEvenIfShortInCharacters() {
        assertViolation("🔒".repeat(20), "jane");
    }

    @Test
    void rejectsThePasswordBeingTheUsername() {
        assertViolation("Jane.Doe.Long", "jane.doe.long");
    }

    private static void assertViolation(String password, String username) {
        assertThatThrownBy(() -> PasswordPolicy.check(password, username))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode").isEqualTo(IdentityErrorCode.PASSWORD_POLICY_VIOLATION);
    }
}
