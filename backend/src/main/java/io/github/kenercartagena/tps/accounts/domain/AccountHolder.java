package io.github.kenercartagena.tps.accounts.domain;

import io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * A person or business that owns accounts, identified by one identity document. The
 * schema is defined by migration {@code V5}.
 *
 * <p>The document number is personal data: it is returned only to users allowed to read
 * accounts, and never logged.
 */
@Entity
@Table(name = "account_holders")
public class AccountHolder {

    public static final int MAX_FULL_NAME_LENGTH = 150;

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private HolderType holderType;

    @Column(nullable = false, length = MAX_FULL_NAME_LENGTH)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private DocumentType documentType;

    @Column(nullable = false, length = 20, updatable = false)
    private String documentNumber;

    @Column(updatable = false)
    private @Nullable UUID createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    /** For JPA only. */
    protected AccountHolder() {
    }

    private AccountHolder(HolderType holderType, String fullName, DocumentType documentType,
            String documentNumber, @Nullable UUID createdBy, Instant now) {
        this.holderType = Objects.requireNonNull(holderType, "holderType");
        this.fullName = fullName.strip();
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.documentNumber = DocumentType.normalize(documentNumber);
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Registers a holder. The document number may contain spaces or dashes; it is stored
     * normalized.
     *
     * @throws BusinessRuleViolationException {@code INVALID_HOLDER_DOCUMENT} if the number is
     *         not valid for its type, or a business is not identified by its RTN
     */
    public static AccountHolder register(HolderType holderType, String fullName,
            DocumentType documentType, String documentNumber, @Nullable UUID createdBy, Instant now) {
        if (!holderType.accepts(documentType)) {
            throw new BusinessRuleViolationException(AccountsErrorCode.INVALID_HOLDER_DOCUMENT,
                    "A business must be identified by its RTN.");
        }
        if (!documentType.isValid(documentNumber)) {
            throw new BusinessRuleViolationException(AccountsErrorCode.INVALID_HOLDER_DOCUMENT,
                    "The %s number %s.".formatted(documentType, documentType.formatDescription()));
        }
        if (fullName.isBlank() || fullName.strip().length() > MAX_FULL_NAME_LENGTH) {
            throw new IllegalArgumentException("fullName must be 1 to 150 characters");
        }
        return new AccountHolder(holderType, fullName, documentType, documentNumber, createdBy, now);
    }

    public UUID getId() {
        return id;
    }

    public HolderType getHolderType() {
        return holderType;
    }

    public String getFullName() {
        return fullName;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public @Nullable UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
