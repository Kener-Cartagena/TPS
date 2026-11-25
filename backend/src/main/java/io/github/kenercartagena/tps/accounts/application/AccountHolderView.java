package io.github.kenercartagena.tps.accounts.application;

import io.github.kenercartagena.tps.accounts.domain.AccountHolder;
import io.github.kenercartagena.tps.accounts.domain.DocumentType;
import io.github.kenercartagena.tps.accounts.domain.HolderType;
import java.time.Instant;
import java.util.UUID;

/**
 * Read model of an account holder, returned by the API.
 *
 * @param documentNumber normalized: without spaces or dashes
 */
public record AccountHolderView(
        UUID id,
        HolderType holderType,
        String fullName,
        DocumentType documentType,
        String documentNumber,
        Instant createdAt,
        Instant updatedAt) {

    static AccountHolderView of(AccountHolder holder) {
        return new AccountHolderView(holder.getId(), holder.getHolderType(), holder.getFullName(),
                holder.getDocumentType(), holder.getDocumentNumber(), holder.getCreatedAt(),
                holder.getUpdatedAt());
    }
}
