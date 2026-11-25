package io.github.kenercartagena.tps.accounts.web;

import io.github.kenercartagena.tps.accounts.domain.AccountStatus;
import io.github.kenercartagena.tps.accounts.domain.AccountStatusChange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/v1/accounts/{accountId}/status}.
 *
 * @param reason why the status changes; stored in the account's status history
 */
record ChangeAccountStatusRequest(
        @NotNull AccountStatus status,
        @NotBlank @Size(min = 3, max = AccountStatusChange.MAX_REASON_LENGTH) String reason) {
}
