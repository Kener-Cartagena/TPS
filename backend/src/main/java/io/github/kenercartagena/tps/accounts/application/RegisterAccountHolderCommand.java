package io.github.kenercartagena.tps.accounts.application;

import io.github.kenercartagena.tps.accounts.domain.DocumentType;
import io.github.kenercartagena.tps.accounts.domain.HolderType;

/** Input of {@link AccountHolderService#register}. The document number may contain spaces or dashes. */
public record RegisterAccountHolderCommand(
        HolderType holderType,
        String fullName,
        DocumentType documentType,
        String documentNumber) {
}
