package io.github.kenercartagena.tps.accounts.domain;

/** Whether an account holder is a natural person or a business. */
public enum HolderType {

    /** Identified by DNI, passport or RTN. */
    PERSON,
    /** Identified by its RTN (tax id) only. */
    BUSINESS;

    /** Whether a holder of this type can be identified with the given document. */
    public boolean accepts(DocumentType documentType) {
        return this == PERSON || documentType == DocumentType.RTN;
    }
}
