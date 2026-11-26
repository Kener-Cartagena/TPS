package io.github.kenercartagena.tps.transactions.web;

import io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException;
import io.github.kenercartagena.tps.shared.error.ErrorCode;
import io.github.kenercartagena.tps.shared.web.ApiPaths;
import io.github.kenercartagena.tps.transactions.application.TransactionOutcome;
import io.github.kenercartagena.tps.transactions.application.TransactionView;
import io.github.kenercartagena.tps.transactions.domain.TransactionStatus;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Turns the outcome of an operation into its HTTP response, the same way for every
 * operation type and for retries:
 *
 * <ul>
 *   <li>{@code COMPLETED}: {@code 201 Created}, the transaction, and its {@code Location};</li>
 *   <li>{@code REJECTED}: {@code 422} Problem Details with the code of the rule and the
 *       {@code transactionId}, so the rejection can be looked up later;</li>
 *   <li>a retry gets exactly the same response, plus {@value #REPLAYED_HEADER}{@code : true}.</li>
 * </ul>
 */
final class TransactionResponses {

    /** Response header that marks the answer to a retried request. */
    static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private TransactionResponses() {
    }

    static ResponseEntity<TransactionView> of(TransactionOutcome outcome, HttpServletResponse response) {
        TransactionView transaction = outcome.transaction();
        if (outcome.replayed()) {
            // Set on the servlet response, not on the ResponseEntity, so it is also sent
            // when the outcome is an error response (the exception below).
            response.setHeader(REPLAYED_HEADER, "true");
        }
        if (transaction.status() == TransactionStatus.REJECTED) {
            throw new BusinessRuleViolationException(
                    new StoredErrorCode(Objects.requireNonNull(transaction.rejectionCode())),
                    Objects.requireNonNull(transaction.rejectionDetail()))
                    .withProperty("transactionId", transaction.id());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(ApiPaths.API_V1 + "/transactions/{transactionId}")
                .buildAndExpand(transaction.id()).toUri();
        return ResponseEntity.created(location).body(transaction);
    }

    /** The error code a rejected transaction stored, which may come from another module. */
    private record StoredErrorCode(String code) implements ErrorCode {
    }
}
