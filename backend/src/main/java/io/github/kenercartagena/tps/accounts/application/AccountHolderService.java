package io.github.kenercartagena.tps.accounts.application;

import io.github.kenercartagena.tps.accounts.domain.AccountHolder;
import io.github.kenercartagena.tps.accounts.domain.AccountHolderRepository;
import io.github.kenercartagena.tps.accounts.domain.AccountsErrorCode;
import io.github.kenercartagena.tps.accounts.domain.DocumentType;
import io.github.kenercartagena.tps.shared.error.ConflictException;
import io.github.kenercartagena.tps.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account holder use cases. Logs carry ids only: names and document numbers are personal
 * data and never logged.
 */
@Service
@Transactional
public class AccountHolderService {

    private static final Logger log = LoggerFactory.getLogger(AccountHolderService.class);

    private final AccountHolderRepository holders;
    private final Clock clock;

    AccountHolderService(AccountHolderRepository holders, Clock clock) {
        this.holders = holders;
        this.clock = clock;
    }

    public AccountHolderView register(RegisterAccountHolderCommand command, UUID actorId) {
        AccountHolder holder = AccountHolder.register(command.holderType(), command.fullName(),
                command.documentType(), command.documentNumber(), actorId, clock.instant());
        if (holders.existsByDocumentNumberAndDocumentType(holder.getDocumentNumber(),
                holder.getDocumentType())) {
            throw alreadyRegistered(null);
        }
        try {
            // Flush now so a concurrent registration of the same document fails here (409).
            holders.saveAndFlush(holder);
        } catch (DataIntegrityViolationException ex) {
            throw alreadyRegistered(ex);
        }
        log.info("Account holder {} registered by {}", holder.getId(), actorId);
        return AccountHolderView.of(holder);
    }

    @Transactional(readOnly = true)
    public AccountHolderView get(UUID holderId) {
        return holders.findById(holderId).map(AccountHolderView::of)
                .orElseThrow(() -> notFound(holderId));
    }

    /**
     * One page of holders sorted by name, optionally only those with a document number
     * (in any format: spaces and dashes are ignored).
     */
    @Transactional(readOnly = true)
    public Page<AccountHolderView> list(@Nullable String documentNumber, int page, int size) {
        PageRequest request = PageRequest.of(page, size, Sort.by("fullName", "id"));
        Page<AccountHolder> result = documentNumber == null || documentNumber.isBlank()
                ? holders.findAll(request)
                : holders.findByDocumentNumber(DocumentType.normalize(documentNumber), request);
        return result.map(AccountHolderView::of);
    }

    static ResourceNotFoundException notFound(UUID holderId) {
        return new ResourceNotFoundException(AccountsErrorCode.ACCOUNT_HOLDER_NOT_FOUND,
                "Account holder '%s' was not found.".formatted(holderId));
    }

    /** The message does not repeat the document number: it is personal data. */
    private static ConflictException alreadyRegistered(@Nullable Throwable cause) {
        String message = "An account holder with this identity document is already registered.";
        return cause == null
                ? new ConflictException(AccountsErrorCode.ACCOUNT_HOLDER_ALREADY_EXISTS, message)
                : new ConflictException(AccountsErrorCode.ACCOUNT_HOLDER_ALREADY_EXISTS, message, cause);
    }
}
