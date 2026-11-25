package io.github.kenercartagena.tps.accounts.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The 12-digit number that identifies an account to people: 10 digits from a database
 * sequence followed by 2 check digits computed with ISO 7064 MOD 97-10, the algorithm of
 * the IBAN.
 *
 * <p>The check digits make the whole number, read as an integer, leave remainder 1 when
 * divided by 97. Any single mistyped digit and almost every swap of two digits break
 * that, so a typo is detected before it reaches the wrong account.
 *
 * <pre>
 *   sequence  1000000000
 *   check     98 - (1000000000 * 100 mod 97) = 98 - 5 = 93   (always two digits: 02 to 98)
 *   number    100000000093      → 100000000093 mod 97 = 1 ✓
 * </pre>
 */
public record AccountNumber(String value) {

    public static final long MIN_SEQUENCE = 1_000_000_000L;
    public static final long MAX_SEQUENCE = 9_999_999_999L;

    /** Shape only; {@link #isValid} also checks the check digits. */
    public static final String FORMAT = "^[0-9]{12}$";

    private static final Pattern FORMAT_PATTERN = Pattern.compile(FORMAT);
    private static final int MODULUS = 97;

    public AccountNumber {
        Objects.requireNonNull(value, "value");
        if (!isValid(value)) {
            throw new IllegalArgumentException("Not a valid account number");
        }
    }

    /** The account number for a value of the database sequence. */
    public static AccountNumber fromSequence(long sequence) {
        if (sequence < MIN_SEQUENCE || sequence > MAX_SEQUENCE) {
            throw new IllegalArgumentException("Account number sequence out of range: " + sequence);
        }
        long checkDigits = 98 - (sequence * 100 % MODULUS);
        return new AccountNumber("%010d%02d".formatted(sequence, checkDigits));
    }

    /** Whether the candidate has 12 digits and valid check digits. */
    public static boolean isValid(String candidate) {
        return FORMAT_PATTERN.matcher(candidate).matches()
                && Long.parseLong(candidate) % MODULUS == 1;
    }

    @Override
    public String toString() {
        return value;
    }
}
