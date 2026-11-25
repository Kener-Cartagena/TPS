package io.github.kenercartagena.tps.accounts.domain;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountHolderRepository extends JpaRepository<AccountHolder, UUID> {

    /** @param documentNumber normalized (see {@link DocumentType#normalize}) */
    boolean existsByDocumentNumberAndDocumentType(String documentNumber, DocumentType documentType);

    /** @param documentNumber normalized (see {@link DocumentType#normalize}) */
    Page<AccountHolder> findByDocumentNumber(String documentNumber, Pageable pageable);
}
