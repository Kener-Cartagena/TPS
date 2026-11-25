package io.github.kenercartagena.tps.ledger.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {

    boolean existsByCode(String code);
}
