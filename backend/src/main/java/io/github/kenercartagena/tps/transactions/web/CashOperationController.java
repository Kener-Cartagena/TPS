package io.github.kenercartagena.tps.transactions.web;

import io.github.kenercartagena.tps.identity.CurrentUser;
import io.github.kenercartagena.tps.identity.Permissions;
import io.github.kenercartagena.tps.shared.web.ApiPaths;
import io.github.kenercartagena.tps.transactions.application.TransactionEngine;
import io.github.kenercartagena.tps.transactions.application.TransactionRequest;
import io.github.kenercartagena.tps.transactions.application.TransactionView;
import io.github.kenercartagena.tps.transactions.domain.IdempotencyKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cash operations at a branch: deposits and withdrawals. */
@RestController
@RequestMapping(ApiPaths.API_V1)
@Tag(name = "Transactions")
class CashOperationController {

    private static final String KEY_DESCRIPTION = """
            Unique per operation: generate one (a UUID is recommended) and send it again, \
            unchanged, when retrying. A retry returns the first result instead of moving \
            money twice.""";

    private static final String OUTCOMES = """
            Responses: 201 with the transaction when it completes; 422 Problem Details with \
            the rule's code (INSUFFICIENT_FUNDS, ACCOUNT_SUSPENDED, ACCOUNT_CLOSED, \
            CURRENCY_MISMATCH) and the transactionId when it is rejected. A retry with the \
            same Idempotency-Key gets the same response plus `Idempotent-Replayed: true`; \
            the same key with a different body is 422 IDEMPOTENCY_KEY_REUSED.""";

    private final TransactionEngine engine;

    CashOperationController(TransactionEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/deposits")
    @PreAuthorize("hasAuthority('" + Permissions.DEPOSIT_EXECUTE + "')")
    @Operation(summary = "Deposit cash into an account",
            description = "The account must be ACTIVE and in the given currency. " + OUTCOMES
                    + " Requires DEPOSIT_EXECUTE.")
    public ResponseEntity<TransactionView> deposit(@AuthenticationPrincipal Jwt token,
            @RequestHeader(IdempotencyKey.HEADER) @Parameter(description = KEY_DESCRIPTION)
            @Pattern(regexp = IdempotencyKey.FORMAT, message = "must be 1 to 64 letters, digits, '.', '_', ':' or '-'")
            String idempotencyKey,
            @Valid @RequestBody CashOperationRequest request, HttpServletResponse response) {
        return submit(request.toDeposit(), idempotencyKey, token, response);
    }

    @PostMapping("/withdrawals")
    @PreAuthorize("hasAuthority('" + Permissions.WITHDRAWAL_EXECUTE + "')")
    @Operation(summary = "Withdraw cash from an account",
            description = "The account must be ACTIVE, in the given currency, and have the "
                    + "funds. " + OUTCOMES + " Requires WITHDRAWAL_EXECUTE.")
    public ResponseEntity<TransactionView> withdraw(@AuthenticationPrincipal Jwt token,
            @RequestHeader(IdempotencyKey.HEADER) @Parameter(description = KEY_DESCRIPTION)
            @Pattern(regexp = IdempotencyKey.FORMAT, message = "must be 1 to 64 letters, digits, '.', '_', ':' or '-'")
            String idempotencyKey,
            @Valid @RequestBody CashOperationRequest request, HttpServletResponse response) {
        return submit(request.toWithdrawal(), idempotencyKey, token, response);
    }

    private ResponseEntity<TransactionView> submit(TransactionRequest request, String idempotencyKey,
            Jwt token, HttpServletResponse response) {
        return TransactionResponses.of(
                engine.submit(request, new IdempotencyKey(idempotencyKey), CurrentUser.id(token)),
                response);
    }
}
