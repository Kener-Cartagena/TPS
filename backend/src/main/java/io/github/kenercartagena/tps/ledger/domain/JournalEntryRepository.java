package io.github.kenercartagena.tps.ledger.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

/**
 * Journal entries are only added and read, never updated or deleted, so this repository
 * deliberately exposes no {@code delete} methods (it is not a {@code JpaRepository}).
 */
public interface JournalEntryRepository extends Repository<JournalEntry, UUID> {

    JournalEntry save(JournalEntry entry);

    /** The entry with its postings, in one query. */
    @EntityGraph(attributePaths = "postings")
    Optional<JournalEntry> findWithPostingsById(UUID id);
}
