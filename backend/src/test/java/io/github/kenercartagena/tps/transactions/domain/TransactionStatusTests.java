package io.github.kenercartagena.tps.transactions.domain;

import static io.github.kenercartagena.tps.transactions.domain.TransactionStatus.COMPLETED;
import static io.github.kenercartagena.tps.transactions.domain.TransactionStatus.CREATED;
import static io.github.kenercartagena.tps.transactions.domain.TransactionStatus.FAILED;
import static io.github.kenercartagena.tps.transactions.domain.TransactionStatus.PENDING;
import static io.github.kenercartagena.tps.transactions.domain.TransactionStatus.PROCESSING;
import static io.github.kenercartagena.tps.transactions.domain.TransactionStatus.REJECTED;
import static io.github.kenercartagena.tps.transactions.domain.TransactionStatus.REVERSED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The lifecycle of a transaction, pinned exactly (ADR 0007). */
class TransactionStatusTests {

    @Test
    void createdIsProcessedAtOnceWaitsOrIsRejected() {
        assertThat(targetsOf(CREATED)).containsExactlyInAnyOrder(PROCESSING, PENDING, REJECTED);
    }

    @Test
    void pendingIsProcessedRejectedOrFails() {
        assertThat(targetsOf(PENDING)).containsExactlyInAnyOrder(PROCESSING, REJECTED, FAILED);
    }

    @Test
    void processingEndsCompletedRejectedOrFailed() {
        assertThat(targetsOf(PROCESSING)).containsExactlyInAnyOrder(COMPLETED, REJECTED, FAILED);
    }

    @Test
    void onlyACompletedTransactionCanBeReversed() {
        assertThat(targetsOf(COMPLETED)).containsExactly(REVERSED);
        for (TransactionStatus status : TransactionStatus.values()) {
            assertThat(status.canChangeTo(REVERSED)).as(status.name()).isEqualTo(status == COMPLETED);
        }
    }

    @ParameterizedTest
    @EnumSource(names = {"FAILED", "REJECTED", "REVERSED"})
    void failedRejectedAndReversedAreFinal(TransactionStatus status) {
        assertThat(targetsOf(status)).isEmpty();
    }

    @Test
    void nothingGoesBackToCreatedNorStaysInPlace() {
        for (TransactionStatus status : TransactionStatus.values()) {
            assertThat(status.canChangeTo(CREATED)).as("%s -> CREATED", status).isFalse();
            assertThat(status.canChangeTo(status)).as("%s -> itself", status).isFalse();
        }
    }

    @Test
    void processedMeansItHasItsOutcome() {
        assertThat(EnumSet.allOf(TransactionStatus.class)).filteredOn(TransactionStatus::isProcessed)
                .containsExactlyInAnyOrder(COMPLETED, FAILED, REJECTED, REVERSED);
    }

    @ParameterizedTest
    @EnumSource(TransactionType.class)
    void depositsAndWithdrawalsNeverWaitNorFailNorAreReversedYet(TransactionType type) {
        assertThat(EnumSet.allOf(TransactionStatus.class)).filteredOn(type::uses)
                .containsExactlyInAnyOrder(CREATED, PROCESSING, COMPLETED, REJECTED);
    }

    private static Set<TransactionStatus> targetsOf(TransactionStatus from) {
        Set<TransactionStatus> targets = EnumSet.noneOf(TransactionStatus.class);
        for (TransactionStatus target : TransactionStatus.values()) {
            if (from.canChangeTo(target)) {
                targets.add(target);
            }
        }
        return targets;
    }
}
