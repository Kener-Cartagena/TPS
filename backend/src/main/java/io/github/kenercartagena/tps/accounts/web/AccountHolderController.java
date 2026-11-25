package io.github.kenercartagena.tps.accounts.web;

import io.github.kenercartagena.tps.accounts.application.AccountHolderService;
import io.github.kenercartagena.tps.accounts.application.AccountHolderView;
import io.github.kenercartagena.tps.accounts.domain.DocumentType;
import io.github.kenercartagena.tps.identity.CurrentUser;
import io.github.kenercartagena.tps.identity.Permissions;
import io.github.kenercartagena.tps.shared.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** People and businesses that own accounts. */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/account-holders")
@Tag(name = "Account holders")
class AccountHolderController {

    private static final String CAN_READ = "hasAuthority('" + Permissions.ACCOUNT_READ + "')";
    private static final String CAN_MANAGE = "hasAuthority('" + Permissions.ACCOUNT_MANAGE + "')";

    private final AccountHolderService holders;

    AccountHolderController(AccountHolderService holders) {
        this.holders = holders;
    }

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Register an account holder", description = """
            A person (DNI, RTN or passport) or a business (RTN). One holder per identity \
            document. Requires ACCOUNT_MANAGE.""")
    public ResponseEntity<AccountHolderView> register(@AuthenticationPrincipal Jwt token,
            @Valid @RequestBody RegisterAccountHolderRequest request) {
        AccountHolderView holder = holders.register(request.toCommand(), CurrentUser.id(token));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{holderId}").buildAndExpand(holder.id()).toUri();
        return ResponseEntity.created(location).body(holder);
    }

    @GetMapping
    @PreAuthorize(CAN_READ)
    @Operation(summary = "List account holders", description = """
            Sorted by name. documentNumber finds a holder by its document, with or without \
            dashes. Requires ACCOUNT_READ.""")
    public PagedModel<AccountHolderView> list(
            @RequestParam(required = false) @Size(max = DocumentType.MAX_INPUT_LENGTH)
            @Nullable String documentNumber,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(holders.list(documentNumber, page, size));
    }

    @GetMapping("/{holderId}")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Get an account holder", description = "Requires ACCOUNT_READ.")
    public AccountHolderView get(@PathVariable UUID holderId) {
        return holders.get(holderId);
    }
}
