package io.github.kenercartagena.tps.transactions.web;

import io.github.kenercartagena.tps.identity.Permissions;
import io.github.kenercartagena.tps.shared.web.ApiPaths;
import io.github.kenercartagena.tps.transactions.application.TransactionFilter;
import io.github.kenercartagena.tps.transactions.application.TransactionQueries;
import io.github.kenercartagena.tps.transactions.application.TransactionStatusChangeView;
import io.github.kenercartagena.tps.transactions.application.TransactionView;
import io.github.kenercartagena.tps.transactions.domain.TransactionStatus;
import io.github.kenercartagena.tps.transactions.domain.TransactionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Transactions and their history, for operators and auditors. */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/transactions")
@Tag(name = "Transactions")
@PreAuthorize("hasAuthority('" + Permissions.TRANSACTION_READ + "')")
class TransactionController {

    private final TransactionQueries queries;

    TransactionController(TransactionQueries queries) {
        this.queries = queries;
    }

    @GetMapping
    @Operation(summary = "List transactions", description = """
            Newest first, completed and rejected alike. Optional filters: accountId (money \
            in or out of that account), type, status. Requires TRANSACTION_READ.""")
    public PagedModel<TransactionView> list(
            @RequestParam(required = false) @Nullable UUID accountId,
            @RequestParam(required = false) @Nullable TransactionType type,
            @RequestParam(required = false) @Nullable TransactionStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(queries.list(new TransactionFilter(accountId, type, status), page, size));
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get a transaction", description = "Requires TRANSACTION_READ.")
    public TransactionView get(@PathVariable UUID transactionId) {
        return queries.get(transactionId);
    }

    @GetMapping("/{transactionId}/status-history")
    @Operation(summary = "Get the status history of a transaction", description = """
            Every status it went through, oldest first; the first entry is its creation. \
            Requires TRANSACTION_READ.""")
    public List<TransactionStatusChangeView> statusHistory(@PathVariable UUID transactionId) {
        return queries.statusHistory(transactionId);
    }
}
