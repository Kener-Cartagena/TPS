package io.github.kenercartagena.tps.ledger.web;

import io.github.kenercartagena.tps.identity.Permissions;
import io.github.kenercartagena.tps.ledger.application.JournalEntryView;
import io.github.kenercartagena.tps.ledger.application.LedgerAccountView;
import io.github.kenercartagena.tps.ledger.application.LedgerPostingView;
import io.github.kenercartagena.tps.ledger.application.LedgerQueries;
import io.github.kenercartagena.tps.ledger.application.LedgerVerificationReport;
import io.github.kenercartagena.tps.ledger.application.LedgerVerifier;
import io.github.kenercartagena.tps.shared.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view of the general ledger, for auditors. Every operation requires
 * {@code LEDGER_READ}. There is deliberately no endpoint that posts entries: money only
 * moves through business operations (transactions, Phase 4), which post to the ledger
 * internally.
 */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/ledger")
@PreAuthorize("hasAuthority('" + Permissions.LEDGER_READ + "')")
@Tag(name = "Ledger")
class LedgerController {

    private final LedgerQueries queries;
    private final LedgerVerifier verifier;

    LedgerController(LedgerQueries queries, LedgerVerifier verifier) {
        this.queries = queries;
        this.verifier = verifier;
    }

    @GetMapping("/accounts")
    @Operation(summary = "List ledger accounts",
            description = "Internal and customer accounts of the books, sorted by code. Requires LEDGER_READ.")
    public PagedModel<LedgerAccountView> listAccounts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(queries.listAccounts(page, size));
    }

    @GetMapping("/accounts/{ledgerAccountId}")
    @Operation(summary = "Get a ledger account", description = "Requires LEDGER_READ.")
    public LedgerAccountView getAccount(@PathVariable UUID ledgerAccountId) {
        return queries.getAccount(ledgerAccountId);
    }

    @GetMapping("/accounts/{ledgerAccountId}/postings")
    @Operation(summary = "List the postings of a ledger account",
            description = "Newest first, with the balance after each posting. Requires LEDGER_READ.")
    public PagedModel<LedgerPostingView> listPostings(@PathVariable UUID ledgerAccountId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(queries.postings(ledgerAccountId, page, size));
    }

    @GetMapping("/journal-entries/{journalEntryId}")
    @Operation(summary = "Get a journal entry", description = "With all its lines. Requires LEDGER_READ.")
    public JournalEntryView getJournalEntry(@PathVariable UUID journalEntryId) {
        return queries.getJournalEntry(journalEntryId);
    }

    @GetMapping("/verification")
    @Operation(summary = "Verify the ledger", description = """
            Recomputes every balance from the postings and checks that every journal entry \
            is balanced and that total debits equal total credits in each currency. \
            Scans the whole ledger. Requires LEDGER_READ.""")
    public LedgerVerificationReport verify() {
        return verifier.verify();
    }
}
