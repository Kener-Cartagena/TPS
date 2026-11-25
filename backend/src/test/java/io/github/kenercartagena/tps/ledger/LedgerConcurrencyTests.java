package io.github.kenercartagena.tps.ledger;

import static io.github.kenercartagena.tps.ledger.JournalLine.credit;
import static io.github.kenercartagena.tps.ledger.JournalLine.debit;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.kenercartagena.tps.TestcontainersConfiguration;
import io.github.kenercartagena.tps.ledger.application.LedgerVerifier;
import io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException;
import io.github.kenercartagena.tps.shared.money.Currency;
import io.github.kenercartagena.tps.shared.money.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Many entries posted at the same time on the same accounts, against a real PostgreSQL
 * database. Each test releases all threads at once to maximize contention, then checks
 * the financial invariants:
 *
 * <ul>
 *   <li>no update is lost (every posting is reflected in the balance),</li>
 *   <li>entries in opposite directions never deadlock (accounts are locked in id order),</li>
 *   <li>an account that forbids it is never overdrawn, however many debits race,</li>
 *   <li>the whole ledger still verifies as consistent.</li>
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LedgerConcurrencyTests {

    /** Below the connection pool size (10), so threads wait for locks, not for connections. */
    private static final int THREADS = 8;

    @Autowired
    private Ledger ledger;

    @Autowired
    private LedgerVerifier verifier;

    @AfterEach
    void ledgerIsStillConsistent() {
        assertThat(verifier.verify().consistent()).as("ledger verification").isTrue();
    }

    @Test
    void concurrentPostingsNeverLoseAnUpdate() throws Exception {
        UUID source = open(LedgerAccountType.EQUITY, true);
        UUID account = open(LedgerAccountType.LIABILITY, false);
        int postings = 40;

        List<Outcome> outcomes = race(postings, i -> () -> ledger.post(entry(
                debit(source, hnl("1.00")), credit(account, hnl("1.00")))));

        assertThat(outcomes).containsOnly(Outcome.POSTED);
        assertThat(ledger.balanceOf(account).balance()).isEqualTo(hnl("40.00"));
        // Every running balance from 1.00 to 40.00 appears exactly once: they were applied
        // one after the other, never two on the same starting balance.
        assertThat(ledger.postingsOf(account, 0, 100).getContent())
                .extracting(posting -> posting.balanceAfter().amount().intValue())
                .containsExactlyInAnyOrderElementsOf(range(1, postings));
    }

    @Test
    void opposingEntriesDoNotDeadlock() throws Exception {
        UUID source = open(LedgerAccountType.EQUITY, true);
        UUID first = open(LedgerAccountType.LIABILITY, false);
        UUID second = open(LedgerAccountType.LIABILITY, false);
        ledger.post(entry(debit(source, hnl("200.00")), credit(first, hnl("100.00")),
                credit(second, hnl("100.00"))));

        // Half move money first -> second, half second -> first, at the same time.
        List<Outcome> outcomes = race(100, i -> () -> ledger.post(i % 2 == 0
                ? entry(debit(first, hnl("1.00")), credit(second, hnl("1.00")))
                : entry(debit(second, hnl("1.00")), credit(first, hnl("1.00")))));

        assertThat(outcomes).containsOnly(Outcome.POSTED);
        assertThat(ledger.balanceOf(first).balance()).isEqualTo(hnl("100.00"));
        assertThat(ledger.balanceOf(second).balance()).isEqualTo(hnl("100.00"));
    }

    @Test
    void racingDebitsNeverOverdrawAnAccount() throws Exception {
        UUID sink = open(LedgerAccountType.EQUITY, true);
        UUID account = open(LedgerAccountType.LIABILITY, false);
        ledger.post(entry(debit(sink, hnl("10.00")), credit(account, hnl("10.00"))));

        List<Outcome> outcomes = race(30, i -> () -> ledger.post(entry(
                debit(account, hnl("1.00")), credit(sink, hnl("1.00")))));

        assertThat(outcomes).filteredOn(Outcome.POSTED::equals).hasSize(10);
        assertThat(outcomes).filteredOn(Outcome.INSUFFICIENT_FUNDS::equals).hasSize(20);
        assertThat(ledger.balanceOf(account).balance()).isEqualTo(hnl("0.00"));
    }

    // ---- Helpers -------------------------------------------------------------------

    private enum Outcome { POSTED, INSUFFICIENT_FUNDS }

    /**
     * Runs the tasks on {@value #THREADS} threads, all released at the same moment.
     * Insufficient funds is an expected outcome; any other failure (a deadlock, a lost
     * lock) fails the test.
     */
    private static List<Outcome> race(int tasks, IntFunction<Runnable> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                Runnable work = task.apply(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        work.run();
                        return Outcome.POSTED;
                    } catch (BusinessRuleViolationException ex) {
                        if (ex.getErrorCode().code().equals("INSUFFICIENT_FUNDS")) {
                            return Outcome.INSUFFICIENT_FUNDS;
                        }
                        throw ex;
                    }
                }));
            }
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private UUID open(LedgerAccountType type, boolean allowNegative) {
        return ledger.openAccount(new NewLedgerAccount(LedgerIntegrationTests.uniqueCode(),
                "Concurrency test", type, Currency.HNL, allowNegative));
    }

    private static JournalEntryRequest entry(JournalLine... lines) {
        return new JournalEntryRequest("Concurrency test", null, null, List.of(lines));
    }

    private static Money hnl(String amount) {
        return Money.of(amount, Currency.HNL);
    }

    private static List<Integer> range(int from, int to) {
        List<Integer> values = new ArrayList<>();
        for (int value = from; value <= to; value++) {
            values.add(value);
        }
        return values;
    }
}
