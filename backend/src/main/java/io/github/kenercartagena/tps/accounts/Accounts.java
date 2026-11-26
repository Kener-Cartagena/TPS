package io.github.kenercartagena.tps.accounts;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Customer accounts as other modules see them: the public API of the accounts module.
 * The transaction engine uses it to check, before moving money, that each account exists,
 * uses the right currency and allows the movement in its current status.
 */
public interface Accounts {

    /**
     * Locks the accounts for a money movement and returns their current state.
     *
     * <p>Each account is locked with {@code SELECT ... FOR SHARE}, in ascending id order,
     * until the caller's transaction ends. Many movements can hold the lock on the same
     * account at once, but its status cannot change until they finish: a movement never
     * debits an account that was suspended after it checked the status.
     *
     * <p>Lock order across modules: accounts first (this method), then their ledger
     * accounts ({@code Ledger.lockForPosting}). Changing a status follows the same order,
     * so the two never deadlock.
     *
     * <p>Must be called inside the caller's database transaction.
     *
     * @return each account, by id
     * @throws io.github.kenercartagena.tps.shared.error.ResourceNotFoundException
     *         {@code ACCOUNT_NOT_FOUND} if an account does not exist
     */
    Map<UUID, OperableAccount> lockForOperation(Set<UUID> accountIds);
}
