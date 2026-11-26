package io.github.kenercartagena.tps.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kenercartagena.tps.TestcontainersConfiguration;
import io.github.kenercartagena.tps.identity.TestUsers;
import io.github.kenercartagena.tps.identity.domain.Role;
import io.github.kenercartagena.tps.identity.security.SecurityProperties;
import io.github.kenercartagena.tps.ledger.application.LedgerVerifier;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/**
 * End-to-end deposits and withdrawals against a running server and a real PostgreSQL
 * database, with real tokens: the lifecycle, the status and funds rules, idempotent
 * retries, and the history an auditor reads. After every test the whole ledger must still
 * verify as consistent.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionsIntegrationTests {

    private static final String DEPOSITS = "/api/v1/deposits";
    private static final String WITHDRAWALS = "/api/v1/withdrawals";
    private static final String TRANSACTIONS = "/api/v1/transactions";
    private static final String REPLAYED = "Idempotent-Replayed";

    @Autowired
    private RestTestClient client;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private LedgerVerifier verifier;

    private String operator;
    private String otherOperator;
    private String auditor;

    @BeforeAll
    void createUsers() {
        TestUsers users = new TestUsers(client, securityProperties);
        operator = users.tokenFor(Role.OPERATOR);
        otherOperator = users.tokenFor(Role.OPERATOR);
        auditor = users.tokenFor(Role.AUDITOR);
    }

    @AfterEach
    void ledgerIsStillConsistent() {
        assertThat(verifier.verify().consistent()).as("ledger verification").isTrue();
    }

    // ---- Deposits and withdrawals --------------------------------------------------

    @Test
    void aDepositCompletesAndTheMoneyIsInTheAccount() {
        AccountRef account = newAccount("HNL");

        TransactionBody deposit = post(operator, DEPOSITS, newKey(), cash(account.id(), "1000.00", "HNL", "Opening deposit"))
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", ".*/api/v1/transactions/[0-9a-f-]{36}$")
                .expectHeader().doesNotExist(REPLAYED)
                .expectBody(TransactionBody.class).returnResult().getResponseBody();

        assertThat(deposit.type()).isEqualTo("DEPOSIT");
        assertThat(deposit.status()).isEqualTo("COMPLETED");
        assertThat(deposit.amount()).isEqualTo("1000.00");
        assertThat(deposit.destinationAccountId()).isEqualTo(account.id());
        assertThat(deposit.sourceAccountId()).isNull();
        assertThat(deposit.journalEntryId()).isNotNull();
        assertThat(deposit.processedAt()).isNotNull();
        assertBalance(account, "1000.00");
        assertThat(lifecycleOf(deposit.id())).containsExactly("CREATED", "PROCESSING", "COMPLETED");
    }

    @Test
    void theJournalEntryOfATransactionReferencesIt() {
        AccountRef account = newAccount("HNL");
        TransactionBody deposit = completed(DEPOSITS, account, "250.00");

        get(auditor, "/api/v1/ledger/journal-entries/" + deposit.journalEntryId())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.reference").isEqualTo(deposit.id().toString())
                .jsonPath("$.description").isEqualTo("Cash deposit")
                .jsonPath("$.lines.length()").isEqualTo(2);
        get(operator, "/api/v1/accounts/" + account.id() + "/entries")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].direction").isEqualTo("CREDIT")
                .jsonPath("$.content[0].amount").isEqualTo("250.00")
                .jsonPath("$.content[0].balanceAfter").isEqualTo("250.00");
    }

    @Test
    void aWithdrawalWithinTheFundsCompletes() {
        AccountRef account = newAccount("USD");
        completed(DEPOSITS, account, "100.00");

        TransactionBody withdrawal = completed(WITHDRAWALS, account, "40.50");

        assertThat(withdrawal.type()).isEqualTo("WITHDRAWAL");
        assertThat(withdrawal.sourceAccountId()).isEqualTo(account.id());
        assertThat(withdrawal.currency()).isEqualTo("USD");
        assertBalance(account, "59.50");
    }

    @Test
    void aWithdrawalOfTheWholeBalanceLeavesZero() {
        AccountRef account = newAccount("HNL");
        completed(DEPOSITS, account, "75.25");

        completed(WITHDRAWALS, account, "75.25");

        assertBalance(account, "0.00");
    }

    // ---- Rejections ------------------------------------------------------------------

    @Test
    void aWithdrawalBeyondTheFundsIsRejectedAndRecorded() {
        AccountRef account = newAccount("HNL");
        completed(DEPOSITS, account, "50.00");

        UUID transactionId = rejected(WITHDRAWALS, account, "50.01", "INSUFFICIENT_FUNDS");

        TransactionBody stored = transaction(transactionId);
        assertThat(stored.status()).isEqualTo("REJECTED");
        assertThat(stored.rejectionCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(stored.rejectionDetail()).isNotBlank();
        assertThat(stored.journalEntryId()).isNull();
        assertThat(stored.processedAt()).isNotNull();
        assertThat(lifecycleOf(transactionId)).containsExactly("CREATED", "PROCESSING", "REJECTED");
        get(operator, TRANSACTIONS + "/" + transactionId + "/status-history")
                .expectStatus().isOk()
                .expectBody().jsonPath("$[2].reason").isEqualTo("INSUFFICIENT_FUNDS");
        assertBalance(account, "50.00");
    }

    @Test
    void aSuspendedAccountReceivesAndSendsNothingUntilItIsReactivated() {
        AccountRef account = newAccount("HNL");
        completed(DEPOSITS, account, "100.00");
        changeStatus(account, "SUSPENDED");

        rejected(DEPOSITS, account, "10.00", "ACCOUNT_SUSPENDED");
        rejected(WITHDRAWALS, account, "10.00", "ACCOUNT_SUSPENDED");
        assertBalance(account, "100.00");

        changeStatus(account, "ACTIVE");
        completed(WITHDRAWALS, account, "10.00");
        assertBalance(account, "90.00");
    }

    @Test
    void aClosedAccountReceivesNothing() {
        AccountRef account = newAccount("HNL");
        changeStatus(account, "CLOSED");

        rejected(DEPOSITS, account, "10.00", "ACCOUNT_CLOSED");
    }

    @Test
    void theCurrencyMustBeTheAccountsCurrency() {
        AccountRef account = newAccount("HNL");

        rejected(DEPOSITS, account, "10.00", "CURRENCY_MISMATCH", "USD");

        assertBalance(account, "0.00");
    }

    @Test
    void anUnknownAccountIsNotFoundAndNothingIsRecorded() {
        String key = newKey();

        post(operator, DEPOSITS, key, cash(UUID.randomUUID(), "10.00", "HNL", null))
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("ACCOUNT_NOT_FOUND");

        // The key was not used: nothing was recorded, so it can still be used.
        AccountRef account = newAccount("HNL");
        post(operator, DEPOSITS, key, cash(account.id(), "10.00", "HNL", null))
                .expectStatus().isCreated()
                .expectHeader().doesNotExist(REPLAYED);
    }

    // ---- Idempotency -----------------------------------------------------------------

    @Test
    void aRetryReturnsTheFirstResultAndMovesNoMoney() {
        AccountRef account = newAccount("HNL");
        String key = newKey();
        Map<String, Object> body = cash(account.id(), "300.00", "HNL", "Salary");

        TransactionBody first = post(operator, DEPOSITS, key, body)
                .expectStatus().isCreated()
                .expectBody(TransactionBody.class).returnResult().getResponseBody();
        TransactionBody retry = post(operator, DEPOSITS, key, body)
                .expectStatus().isCreated()
                .expectHeader().valueEquals(REPLAYED, "true")
                .expectHeader().valueMatches("Location", ".*/api/v1/transactions/" + first.id() + "$")
                .expectBody(TransactionBody.class).returnResult().getResponseBody();

        assertThat(retry).isEqualTo(first);
        assertBalance(account, "300.00");
    }

    @Test
    void aRetryIsTheSameRequestHoweverTheAmountIsWritten() {
        AccountRef account = newAccount("HNL");
        String key = newKey();
        completed(account, key, "300");

        post(operator, DEPOSITS, key, cash(account.id(), "300.00", "HNL", null))
                .expectStatus().isCreated()
                .expectHeader().valueEquals(REPLAYED, "true");
        assertBalance(account, "300.00");
    }

    @Test
    void aRetryOfARejectedRequestGetsTheSameRejection() {
        AccountRef account = newAccount("HNL");
        String key = newKey();
        Map<String, Object> body = cash(account.id(), "10.00", "HNL", null);
        UUID first = post(operator, WITHDRAWALS, key, body)
                .expectStatus().isEqualTo(422)
                .expectHeader().doesNotExist(REPLAYED)
                .expectBody(ProblemBody.class).returnResult().getResponseBody().transactionId();

        // Funds arrive meanwhile, but the retry is still answered with the first result.
        completed(DEPOSITS, account, "50.00");
        post(operator, WITHDRAWALS, key, body)
                .expectStatus().isEqualTo(422)
                .expectHeader().valueEquals(REPLAYED, "true")
                .expectBody()
                .jsonPath("$.code").isEqualTo("INSUFFICIENT_FUNDS")
                .jsonPath("$.transactionId").isEqualTo(first.toString());
        assertBalance(account, "50.00");
    }

    @Test
    void aKeyCannotBeReusedForADifferentRequest() {
        AccountRef account = newAccount("HNL");
        String key = newKey();
        completed(account, key, "10.00");

        post(operator, DEPOSITS, key, cash(account.id(), "20.00", "HNL", null))
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        post(operator, WITHDRAWALS, key, cash(account.id(), "10.00", "HNL", null))
                .expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertBalance(account, "10.00");
    }

    @Test
    void keysBelongToTheirUser() {
        AccountRef account = newAccount("HNL");
        String key = newKey();
        Map<String, Object> body = cash(account.id(), "10.00", "HNL", null);

        post(operator, DEPOSITS, key, body).expectStatus().isCreated();
        post(otherOperator, DEPOSITS, key, body)
                .expectStatus().isCreated()
                .expectHeader().doesNotExist(REPLAYED);

        assertBalance(account, "20.00");
    }

    // ---- Request validation ----------------------------------------------------------

    @Test
    void theIdempotencyKeyIsRequiredAndValidated() {
        AccountRef account = newAccount("HNL");
        Map<String, Object> body = cash(account.id(), "10.00", "HNL", null);

        client.post().uri(DEPOSITS).headers(h -> h.setBearerAuth(operator))
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detail").value(String.class,
                        detail -> assertThat(detail).contains("Idempotency-Key"));
        post(operator, DEPOSITS, "not a valid key", body)
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("VALIDATION_FAILED");
        assertBalance(account, "0.00");
    }

    @Test
    void amountsMustBePositiveWithAtMostTwoDecimals() {
        AccountRef account = newAccount("HNL");

        for (String amount : List.of("0", "0.00", "-5.00", "10.001", "1e3", "abc")) {
            post(operator, DEPOSITS, newKey(), cash(account.id(), amount, "HNL", null))
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                    .jsonPath("$.errors[0].field").isEqualTo("amount");
        }
        assertBalance(account, "0.00");
    }

    @Test
    void aBlankDescriptionIsRejected() {
        AccountRef account = newAccount("HNL");

        post(operator, DEPOSITS, newKey(), cash(account.id(), "1.00", "HNL", "   "))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("description");
    }

    // ---- Reading transactions --------------------------------------------------------

    @Test
    void transactionsOfAnAccountAreListedNewestFirstAndFiltered() {
        AccountRef account = newAccount("HNL");
        TransactionBody deposit = completed(DEPOSITS, account, "100.00");
        TransactionBody withdrawal = completed(WITHDRAWALS, account, "30.00");
        UUID rejectedId = rejected(WITHDRAWALS, account, "500.00", "INSUFFICIENT_FUNDS");

        get(auditor, TRANSACTIONS + "?accountId=" + account.id())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page.totalElements").isEqualTo(3)
                .jsonPath("$.content[0].id").isEqualTo(rejectedId.toString())
                .jsonPath("$.content[1].id").isEqualTo(withdrawal.id().toString())
                .jsonPath("$.content[2].id").isEqualTo(deposit.id().toString());
        get(auditor, TRANSACTIONS + "?accountId=" + account.id() + "&type=WITHDRAWAL&status=COMPLETED")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].id").isEqualTo(withdrawal.id().toString());
    }

    @Test
    void anUnknownTransactionIsNotFound() {
        UUID unknown = UUID.randomUUID();

        get(auditor, TRANSACTIONS + "/" + unknown)
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("TRANSACTION_NOT_FOUND");
        get(auditor, TRANSACTIONS + "/" + unknown + "/status-history")
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("TRANSACTION_NOT_FOUND");
    }

    @Test
    void theAuditorReadsButCannotMoveMoney() {
        AccountRef account = newAccount("HNL");

        post(auditor, DEPOSITS, newKey(), cash(account.id(), "10.00", "HNL", null))
                .expectStatus().isForbidden();
        post(auditor, WITHDRAWALS, newKey(), cash(account.id(), "10.00", "HNL", null))
                .expectStatus().isForbidden();
    }

    // ---- Helpers -------------------------------------------------------------------

    private TransactionBody completed(String path, AccountRef account, String amount) {
        return post(operator, path, newKey(), cash(account.id(), amount, account.currency(), null))
                .expectStatus().isCreated()
                .expectBody(TransactionBody.class).returnResult().getResponseBody();
    }

    private TransactionBody completed(AccountRef account, String key, String amount) {
        return post(operator, DEPOSITS, key, cash(account.id(), amount, account.currency(), null))
                .expectStatus().isCreated()
                .expectBody(TransactionBody.class).returnResult().getResponseBody();
    }

    private UUID rejected(String path, AccountRef account, String amount, String code) {
        return rejected(path, account, amount, code, account.currency());
    }

    /** @return the id of the rejected transaction, from the error response */
    private UUID rejected(String path, AccountRef account, String amount, String code, String currency) {
        ProblemBody problem = post(operator, path, newKey(), cash(account.id(), amount, currency, null))
                .expectStatus().isEqualTo(422)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(ProblemBody.class).returnResult().getResponseBody();
        assertThat(problem.code()).isEqualTo(code);
        assertThat(problem.transactionId()).isNotNull();
        return problem.transactionId();
    }

    private ResponseSpec post(String token, String path, String key, Map<String, Object> body) {
        return client.post().uri(path)
                .headers(h -> {
                    h.setBearerAuth(token);
                    h.set("Idempotency-Key", key);
                })
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange();
    }

    private ResponseSpec get(String token, String uri) {
        return client.get().uri(uri).headers(h -> h.setBearerAuth(token)).exchange();
    }

    private TransactionBody transaction(UUID id) {
        return get(auditor, TRANSACTIONS + "/" + id).expectStatus().isOk()
                .expectBody(TransactionBody.class).returnResult().getResponseBody();
    }

    private List<String> lifecycleOf(UUID transactionId) {
        StatusChangeBody[] history = get(auditor, TRANSACTIONS + "/" + transactionId + "/status-history")
                .expectStatus().isOk()
                .expectBody(StatusChangeBody[].class).returnResult().getResponseBody();
        return java.util.Arrays.stream(history).map(StatusChangeBody::toStatus).toList();
    }

    private void assertBalance(AccountRef account, String expected) {
        get(operator, "/api/v1/accounts/" + account.id() + "/balance")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ledgerBalance").isEqualTo(expected)
                .jsonPath("$.availableBalance").isEqualTo(expected);
    }

    private void changeStatus(AccountRef account, String status) {
        client.put().uri("/api/v1/accounts/" + account.id() + "/status").headers(h -> h.setBearerAuth(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", status, "reason", "Integration test"))
                .exchange()
                .expectStatus().isOk();
    }

    private AccountRef newAccount(String currency) {
        UUID holderId = client.post().uri("/api/v1/account-holders").headers(h -> h.setBearerAuth(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("holderType", "PERSON", "fullName", "Transactions Test",
                        "documentType", "DNI",
                        "documentNumber", "0801" + ThreadLocalRandom.current().nextLong(100_000_000L, 1_000_000_000L)))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(IdBody.class).returnResult().getResponseBody().id();
        return client.post().uri("/api/v1/accounts").headers(h -> h.setBearerAuth(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("holderId", holderId, "type", "SAVINGS", "currency", currency))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(AccountRef.class).returnResult().getResponseBody();
    }

    private static Map<String, Object> cash(UUID accountId, String amount, String currency,
            @Nullable String description) {
        Map<String, Object> body = new HashMap<>();
        body.put("accountId", accountId);
        body.put("amount", amount);
        body.put("currency", currency);
        if (description != null) {
            body.put("description", description);
        }
        return body;
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    record IdBody(UUID id) {
    }

    record AccountRef(UUID id, String currency) {
    }

    record TransactionBody(UUID id, String type, String status, String amount, String currency,
            @Nullable UUID sourceAccountId, @Nullable UUID destinationAccountId,
            @Nullable String description, @Nullable UUID journalEntryId,
            @Nullable String rejectionCode, @Nullable String rejectionDetail, UUID createdBy,
            Instant createdAt, @Nullable Instant processedAt) {
    }

    record ProblemBody(String code, String detail, @Nullable UUID transactionId) {
    }

    record StatusChangeBody(@Nullable String fromStatus, String toStatus, @Nullable String reason) {
    }
}
