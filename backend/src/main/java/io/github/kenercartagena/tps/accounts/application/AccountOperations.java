package io.github.kenercartagena.tps.accounts.application;

import io.github.kenercartagena.tps.accounts.Accounts;
import io.github.kenercartagena.tps.accounts.OperableAccount;
import io.github.kenercartagena.tps.accounts.domain.Account;
import io.github.kenercartagena.tps.accounts.domain.AccountRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Implementation of the {@link Accounts} facade. */
@Service
class AccountOperations implements Accounts {

    private final AccountRepository accounts;

    AccountOperations(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /** {@code MANDATORY}: fails at once if there is no transaction to hold the locks. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<UUID, OperableAccount> lockForOperation(Set<UUID> accountIds) {
        Map<UUID, OperableAccount> locked = new LinkedHashMap<>();
        accountIds.stream().sorted().forEach(id -> {
            Account account = accounts.lockForShareById(id)
                    .orElseThrow(() -> AccountService.notFound(id));
            locked.put(id, new OperableAccount(account.getId(), account.getAccountNumber().value(),
                    account.getCurrency(), account.getStatus(), account.getLedgerAccountId()));
        });
        return locked;
    }
}
