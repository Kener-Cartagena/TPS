package io.github.kenercartagena.tps.transactions;

import static io.github.kenercartagena.tps.transactions.TransactionFixtures.hnl;
import static io.github.kenercartagena.tps.transactions.TransactionFixtures.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kenercartagena.tps.TestcontainersConfiguration;
import io.github.kenercartagena.tps.accounts.AccountStatus;
import io.github.kenercartagena.tps.accounts.application.AccountHolderService;
import io.github.kenercartagena.tps.accounts.application.AccountService;
import io.github.kenercartagena.tps.accounts.application.AccountView;
import io.github.kenercartagena.tps.ledger.application.LedgerVerifier;
import io.github.kenercartagena.tps.transactions.application.TransactionEngine;
import io.github.kenercartagena.tps.transactions.application.TransactionOutcome;
import io.github.kenercartagena.tps.transactions.application.TransactionRequest;
import io.github.kenercartagena.tps.transactions.application.TransactionView;
import io.github.kenercartagena.tps.transactions.domain.IdempotencyKey;
import io.github.kenercartagena.tps.transactions.domain.TransactionStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Many operations at the same time on the same accounts, against a real PostgreSQL
 * database. Each test releases all threads at once to maximize contention, then checks
 * the invariants of the engine:
 *
 * <ul>
 *   <li>the same idempotency key moves money exactly once, however many copies race;</li>
 *   <li>racing withdrawals never overdraw an account, and every refusal is recorded;</li>
 *   <li>deposits and withdrawals on the same accounts never deadlock;</li>
 *   <li>a suspension and the withdrawals in flight are applied one after the other;</li>
 *   <li>an operation that waits too long for a lock gives up and leaves no trace;</li>
 *   <li>the whole ledger still verifies as consistent.</li>
 * </ul>
 *
 * <p>The lock timeout is shortened to 2 seconds, so the timeout test is quick. The other
 * tests only wait for locks held a few milliseconds.
 */
@SpringBootTest(properties = "tps.transactions.lock-timeout=2s")
@Import(TestcontainersConfiguration.class)
class TransactionConcurrencyTests {

    /** Below the connection pool size (10), so threads wait for locks, not for connections. */
    private static final int THREADS = 8;

    @Autowired
    private AccountHolderService holders;

    @Autowired
    private AccountService accounts;

    @Autowired
    private TransactionEngine engine;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private LedgerVerifier verifier;

    private TransactionFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new TransactionFixtures(holders, accounts, engine, jdbc);
    }

    @AfterEach
    void ledgerIsStillConsistent() {
        assertThat(verifier.verify().consistent()).as("ledger verification").isTrue();
    }

    @Test
    void theSameKeyMovesMoneyExactlyOnce() throws Exception {
        AccountView account = fixtures.accountWith("0");
        IdempotencyKey key = newKey();
        TransactionRequest deposit = TransactionRequest.deposit(account.id(), hnl("100.00"), null);

        List<TransactionOutcome> outcomes = race(THREADS, i -> () -> fixtures.submit(deposit, key));

        assertThat(outcomes).extracting(outcome -> outcome.transaction().id()).containsOnly(
                outcomes.getFirst().transaction().id());
        assertThat(outcomes).filteredOn(outcome -> !outcome.replayed()).hasSize(1);
        assertThat(fixtures.balanceOf(account)).isEqualTo(hnl("100.00"));
        assertThat(jdbc.queryForObject("select count(*) from journal_entries where reference = ?",
                Integer.class, outcomes.getFirst().transaction().id().toString())).isEqualTo(1);
    }

    @Test
    void racingWithdrawalsNeverOverdrawAndEveryRefusalIsRecorded() throws Exception {
        AccountView account = fixtures.accountWith("10.00");

        List<TransactionOutcome> outcomes = race(30, i -> () -> fixtures.submit(
                TransactionRequest.withdrawal(account.id(), hnl("1.00"), null), newKey()));

        assertThat(outcomes).filteredOn(outcome -> outcome.transaction().status() == TransactionStatus.COMPLETED)
                .hasSize(10);
        assertThat(outcomes).filteredOn(outcome -> outcome.transaction().status() == TransactionStatus.REJECTED)
                .hasSize(20)
                .allSatisfy(outcome -> assertThat(outcome.transaction().rejectionCode())
                        .isEqualTo("INSUFFICIENT_FUNDS"));
        assertThat(fixtures.balanceOf(account)).isEqualTo(hnl("0.00"));
    }

    @Test
    void depositsAndWithdrawalsOnTheSameAccountsDoNotDeadlock() throws Exception {
        AccountView first = fixtures.accountWith("100.00");
        AccountView second = fixtures.accountWith("100.00");

        // Every operation also locks the settlement account; the customer accounts and the
        // settlement account are always locked in the same order, whatever the operation.
        List<TransactionOutcome> outcomes = race(80, i -> () -> {
            AccountView account = i % 4 < 2 ? first : second;
            TransactionRequest request = i % 2 == 0
                    ? TransactionRequest.deposit(account.id(), hnl("1.00"), null)
                    : TransactionRequest.withdrawal(account.id(), hnl("1.00"), null);
            return fixtures.submit(request, newKey());
        });

        assertThat(outcomes).allSatisfy(outcome ->
                assertThat(outcome.transaction().status()).isEqualTo(TransactionStatus.COMPLETED));
        assertThat(fixtures.balanceOf(first)).isEqualTo(hnl("100.00"));
        assertThat(fixtures.balanceOf(second)).isEqualTo(hnl("100.00"));
    }

    /**
     * The suspension waits for the withdrawals that already locked the account, and the
     * later ones see the new status: no withdrawal completes after the suspension, none is
     * refused for it before.
     */
    @Test
    void aSuspensionAndTheWithdrawalsInFlightHappenOneAfterTheOther() throws Exception {
        AccountView account = fixtures.accountWith("1000.00");

        List<Object> results = race(24, i -> () -> i == 8
                ? accounts.changeStatus(account.id(), AccountStatus.SUSPENDED, "Concurrency test",
                        fixtures.actorId())
                : fixtures.submit(TransactionRequest.withdrawal(account.id(), hnl("1.00"), null), newKey()));

        Instant suspendedAt = jdbc.queryForObject("""
                select changed_at from account_status_changes
                where account_id = ? and to_status = 'SUSPENDED'""", Instant.class, account.id());
        List<TransactionView> withdrawals = results.stream()
                .filter(TransactionOutcome.class::isInstance)
                .map(result -> ((TransactionOutcome) result).transaction())
                .toList();
        assertThat(withdrawals).hasSize(23).allSatisfy(withdrawal -> {
            if (withdrawal.status() == TransactionStatus.COMPLETED) {
                assertThat(withdrawal.processedAt()).isBeforeOrEqualTo(suspendedAt);
            } else {
                assertThat(withdrawal.rejectionCode()).isEqualTo("ACCOUNT_SUSPENDED");
                assertThat(withdrawal.processedAt()).isAfterOrEqualTo(suspendedAt);
            }
        });
        long completed = withdrawals.stream().filter(w -> w.status() == TransactionStatus.COMPLETED).count();
        assertThat(fixtures.balanceOf(account)).isEqualTo(hnl("1000.00").minus(hnl(completed + ".00")));
    }

    /**
     * Another transaction holds the account (here: raw SQL, as a stuck process would). The
     * withdrawal waits for the lock timeout, gives up, and nothing is recorded: the key is
     * still free for a retry, which then completes.
     */
    @Test
    void anOperationThatWaitsTooLongForALockGivesUpAndLeavesNoTrace() throws Exception {
        AccountView account = fixtures.accountWith("50.00");
        IdempotencyKey key = newKey();
        TransactionRequest withdrawal = TransactionRequest.withdrawal(account.id(), hnl("5.00"), null);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        ExecutorService holder = Executors.newSingleThreadExecutor();
        try {
            Future<?> lockHolder = holder.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbc.queryForList("select id from accounts where id = ? for update", account.id());
                locked.countDown();
                await(release);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> fixtures.submit(withdrawal, key))
                    .isInstanceOf(PessimisticLockingFailureException.class);

            release.countDown();
            lockHolder.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            holder.shutdownNow();
        }

        assertThat(jdbc.queryForObject("select count(*) from transactions where idempotency_key = ?",
                Integer.class, key.value())).isZero();
        TransactionOutcome retry = fixtures.submit(withdrawal, key);
        assertThat(retry.replayed()).isFalse();
        assertThat(retry.transaction().status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(fixtures.balanceOf(account)).isEqualTo(hnl("45.00"));
    }

    // ---- Helpers -------------------------------------------------------------------

    /**
     * Runs the tasks on {@value #THREADS} threads, all released at the same moment. Any
     * exception (a deadlock, a lock timeout, a bug) fails the test.
     */
    private static <T> List<T> race(int tasks, IntFunction<Callable<? extends T>> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                Callable<? extends T> work = task.apply(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    return work.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
