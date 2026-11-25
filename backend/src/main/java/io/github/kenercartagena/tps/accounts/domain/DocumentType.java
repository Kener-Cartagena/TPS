package io.github.kenercartagena.tps.accounts.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Identity documents accepted to register an account holder in Honduras.
 *
 * <p>Numbers are stored <em>normalized</em>: without spaces or dashes and in uppercase, so
 * {@code 0801-1990-12345} and {@code 0801199012345} are the same DNI. The database checks
 * the same formats.
 */
public enum DocumentType {

    /** Documento Nacional de Identificación: 13 digits, e.g. {@code 0801-1990-12345}. */
    DNI("^[0-9]{13}$", "must be 13 digits (dashes allowed)"),
    /** Registro Tributario Nacional (tax id): 14 digits, for people and businesses. */
    RTN("^[0-9]{14}$", "must be 14 digits (dashes allowed)"),
    /** Passport of any country: 6 to 20 letters or digits. */
    PASSPORT("^[A-Z0-9]{6,20}$", "must be 6 to 20 letters or digits");

    /** Longest raw input accepted (a 14-digit RTN with dashes and spaces fits). */
    public static final int MAX_INPUT_LENGTH = 25;

    private static final Pattern SEPARATORS = Pattern.compile("[\\s-]");

    private final Pattern format;
    private final String formatDescription;

    DocumentType(String format, String formatDescription) {
        this.format = Pattern.compile(format);
        this.formatDescription = formatDescription;
    }

    /** Removes spaces and dashes and converts to uppercase. */
    public static String normalize(String number) {
        return SEPARATORS.matcher(number).replaceAll("").toUpperCase(Locale.ROOT);
    }

    /** Whether the number (raw or normalized) is valid for this document type. */
    public boolean isValid(String number) {
        return format.matcher(normalize(number)).matches();
    }

    /** The rule, for validation messages: "must be 13 digits ...". */
    public String formatDescription() {
        return formatDescription;
    }
}
