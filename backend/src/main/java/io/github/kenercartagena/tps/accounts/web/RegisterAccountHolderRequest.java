package io.github.kenercartagena.tps.accounts.web;

import io.github.kenercartagena.tps.accounts.application.RegisterAccountHolderCommand;
import io.github.kenercartagena.tps.accounts.domain.AccountHolder;
import io.github.kenercartagena.tps.accounts.domain.DocumentType;
import io.github.kenercartagena.tps.accounts.domain.HolderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/account-holders}.
 *
 * @param documentNumber DNI: 13 digits; RTN: 14 digits (both may include dashes, e.g.
 *                       {@code 0801-1990-12345}); passport: 6 to 20 letters or digits
 */
@ValidHolderDocument
record RegisterAccountHolderRequest(
        @NotNull HolderType holderType,
        @NotBlank @Size(max = AccountHolder.MAX_FULL_NAME_LENGTH) String fullName,
        @NotNull DocumentType documentType,
        @NotBlank @Size(max = DocumentType.MAX_INPUT_LENGTH) String documentNumber) {

    RegisterAccountHolderCommand toCommand() {
        return new RegisterAccountHolderCommand(holderType, fullName, documentType, documentNumber);
    }

    /** Never print the document number: it is personal data. */
    @Override
    public String toString() {
        return "RegisterAccountHolderRequest[holderType=%s, documentType=%s, documentNumber=****]"
                .formatted(holderType, documentType);
    }
}
