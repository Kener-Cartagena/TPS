package io.github.kenercartagena.tps.accounts.web;

import io.github.kenercartagena.tps.accounts.application.AccountBalanceView;
import io.github.kenercartagena.tps.accounts.application.AccountEntryView;
import io.github.kenercartagena.tps.accounts.application.AccountFilter;
import io.github.kenercartagena.tps.accounts.application.AccountService;
import io.github.kenercartagena.tps.accounts.application.AccountStatusChangeView;
import io.github.kenercartagena.tps.accounts.application.AccountView;
import io.github.kenercartagena.tps.accounts.domain.AccountNumber;
import io.github.kenercartagena.tps.accounts.AccountStatus;
import io.github.kenercartagena.tps.identity.CurrentUser;
import io.github.kenercartagena.tps.identity.Permissions;
import io.github.kenercartagena.tps.shared.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Customer accounts: opening, status, balances and movements. */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/accounts")
@Tag(name = "Accounts")
class AccountController {

    private static final String CAN_READ = "hasAuthority('" + Permissions.ACCOUNT_READ + "')";
    private static final String CAN_MANAGE = "hasAuthority('" + Permissions.ACCOUNT_MANAGE + "')";

    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    // ---- Accounts ------------------------------------------------------------------

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Open an account", description = """
            Opens an ACTIVE account with a zero balance and a new 12-digit account number. \
            Requires ACCOUNT_MANAGE.""")
    public ResponseEntity<AccountView> open(@AuthenticationPrincipal Jwt token,
            @Valid @RequestBody OpenAccountRequest request) {
        AccountView account = accounts.open(request.toCommand(), CurrentUser.id(token));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{accountId}").buildAndExpand(account.id()).toUri();
        return ResponseEntity.created(location).body(account);
    }

    @GetMapping
    @PreAuthorize(CAN_READ)
    @Operation(summary = "List accounts", description = """
            Sorted by account number. Optional filters: holderId, status, accountNumber. \
            Requires ACCOUNT_READ.""")
    public PagedModel<AccountView> list(
            @RequestParam(required = false) @Nullable UUID holderId,
            @RequestParam(required = false) @Nullable AccountStatus status,
            @RequestParam(required = false) @Pattern(regexp = AccountNumber.FORMAT,
                    message = "must be 12 digits") @Nullable String accountNumber,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(accounts.list(new AccountFilter(holderId, status, accountNumber),
                page, size));
    }

    @GetMapping("/{accountId}")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Get an account", description = "Requires ACCOUNT_READ.")
    public AccountView get(@PathVariable UUID accountId) {
        return accounts.get(accountId);
    }

    @PutMapping("/{accountId}/status")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Change the status of an account", description = """
            ACTIVE ⇄ SUSPENDED, and ACTIVE or SUSPENDED → CLOSED. Closing is final and \
            requires a zero balance. The reason is stored in the status history. \
            Requires ACCOUNT_MANAGE.""")
    public AccountView changeStatus(@AuthenticationPrincipal Jwt token, @PathVariable UUID accountId,
            @Valid @RequestBody ChangeAccountStatusRequest request) {
        return accounts.changeStatus(accountId, request.status(), request.reason(),
                CurrentUser.id(token));
    }

    // ---- Balances and history ------------------------------------------------------

    @GetMapping("/{accountId}/balance")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Get the balance of an account", description = """
            Ledger balance (all posted movements) and available balance (minus funds on \
            hold; equal for now). Requires ACCOUNT_READ.""")
    public AccountBalanceView balance(@PathVariable UUID accountId) {
        return accounts.balance(accountId);
    }

    @GetMapping("/{accountId}/entries")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "List the movements of an account", description = """
            Newest first, with the balance after each movement. CREDIT is money in, DEBIT \
            money out. Requires ACCOUNT_READ.""")
    public PagedModel<AccountEntryView> entries(@PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(accounts.entries(accountId, page, size));
    }

    @GetMapping("/{accountId}/status-history")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "List the status history of an account",
            description = "Newest first; the oldest entry is the opening. Requires ACCOUNT_READ.")
    public PagedModel<AccountStatusChangeView> statusHistory(@PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(accounts.statusHistory(accountId, page, size));
    }
}
