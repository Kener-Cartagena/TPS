package io.github.kenercartagena.tps.ledger.domain;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

/** Read-only access to postings; they are created through their journal entry. */
public interface PostingRepository extends Repository<Posting, UUID> {

    /** The postings of an account, with their journal entry (for its description). */
    @EntityGraph(attributePaths = "journalEntry")
    Page<Posting> findByLedgerAccountId(UUID ledgerAccountId, Pageable pageable);
}
