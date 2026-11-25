package io.github.kenercartagena.tps.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kenercartagena.tps.TestcontainersConfiguration;
import io.github.kenercartagena.tps.accounts.domain.AccountNumber;
import io.github.kenercartagena.tps.identity.TestUsers;
import io.github.kenercartagena.tps.identity.domain.Role;
import io.github.kenercartagena.tps.identity.security.SecurityProperties;
import io.github.kenercartagena.tps.ledger.JournalEntryRequest;
import io.github.kenercartagena.tps.ledger.JournalLine;
import io.github.kenercartagena.tps.ledger.Ledger;
import io.github.kenercartagena.tps.ledger.PostedJournalEntry;
import io.github.kenercartagena.tps.ledger.SystemLedgerAccounts;
import io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException;
import io.github.kenercartagena.tps.shared.money.Currency;
import io.github.kenercartagena.tps.shared.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;
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
 * End-to-end account flows against a running server and a real PostgreSQL database, with
 * real tokens: an operator registers holders and opens and manages accounts, an auditor
 * reads them and verifies the ledger.
 *
 * <p>Money cannot be moved through the API yet (Phase 4), so movements are posted with the
 * {@link Ledger} facade, as the transaction engine will do.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountsIntegrationTests {

    private static final String HOLDERS = "/api/v1/account-holders";
    private static final String ACCOUNTS = "/api/v1/accounts";

    @Autowired
    private RestTestClient client;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private Ledger ledger;

    private String operator;
    private String auditor;
    private String administrator;

    @BeforeAll
    void createUsers() {
        TestUsers users = new TestUsers(client, securityProperties);
        operator = users.tokenFor(Role.OPERATOR);
        auditor = users.tokenFor(Role.AUDITOR);
        administrator = users.administratorToken();
    }

    // ---- Account holders -----------------------------------------------------------

    @Test
    void operatorRegistersAHolderWithANormalizedDocument() {
        String dni = randomDni();

        HolderBody holder = registerHolder("PERSON", "  María López  ", "DNI", formatted(dni))
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", ".*/api/v1/account-holders/[0-9a-f-]{36}$")
                .expectBody(HolderBody.class).returnResult().getResponseBody();

        assertThat(holder.fullName()).isEqualTo("María López");
        assertThat(holder.documentNumber()).isEqualTo(dni);
        client.get().uri(HOLDERS + "?documentNumber=" + formatted(dni)).headers(h -> h.setBearerAuth(auditor))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].id").isEqualTo(holder.id().toString());
    }

    @Test
    void aDocumentCanOnlyBeRegisteredOnceWhateverItsFormat() {
        String dni = randomDni();
        registerHolder("PERSON", "First", "DNI", dni).expectStatus().isCreated();

        registerHolder("PERSON", "Second", "DNI", formatted(dni))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCOUNT_HOLDER_ALREADY_EXISTS")
                .jsonPath("$.detail").value(String.class, detail -> assertThat(detail).doesNotContain(dni));
    }

    @Test
    void invalidDocumentsAreReportedOnTheirField() {
        registerHolder("PERSON", "Short DNI", "DNI", "0801-1990-1234")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.errors[0].field").isEqualTo("documentNumber")
                .jsonPath("$.errors[0].message").isEqualTo("must be 13 digits (dashes allowed)");

        registerHolder("BUSINESS", "ACME S.A.", "DNI", randomDni())
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("documentType");
    }

    // ---- Opening accounts ----------------------------------------------------------

    @Test
    void operatorOpensAnActiveAccountWithAZeroBalance() {
        UUID holderId = newHolder();

        AccountBody account = openAccount(holderId, "SAVINGS", "HNL")
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", ".*/api/v1/accounts/[0-9a-f-]{36}$")
                .expectBody(AccountBody.class).returnResult().getResponseBody();

        assertThat(AccountNumber.isValid(account.accountNumber())).as("check digits").isTrue();
        assertThat(account.status()).isEqualTo("ACTIVE");
        assertThat(account.currency()).isEqualTo("HNL");
        assertThat(account.holderId()).isEqualTo(holderId);

        get(auditor, ACCOUNTS + "/" + account.id() + "/balance")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ledgerBalance").isEqualTo("0.00")
                .jsonPath("$.availableBalance").isEqualTo("0.00")
                .jsonPath("$.accountNumber").isEqualTo(account.accountNumber());
        get(auditor, ACCOUNTS + "/" + account.id() + "/status-history")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[0].fromStatus").doesNotExist()
                .jsonPath("$.content[0].toStatus").isEqualTo("ACTIVE")
                .jsonPath("$.content[0].reason").isEqualTo("Account opened");
    }

    @Test
    void everyAccountHasItsOwnLedgerAccount() {
        AccountBody account = newAccount("CHECKING", "USD");

        get(auditor, "/api/v1/ledger/accounts/" + account.ledgerAccountId()).expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CUS-" + account.accountNumber())
                .jsonPath("$.type").isEqualTo("LIABILITY")
                .jsonPath("$.currency").isEqualTo("USD")
                .jsonPath("$.allowNegativeBalance").isEqualTo(false)
                .jsonPath("$.balance").isEqualTo("0.00");
    }

    @Test
    void accountForAnUnknownHolderIsNotOpened() {
        openAccount(UUID.randomUUID(), "SAVINGS", "HNL")
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("ACCOUNT_HOLDER_NOT_FOUND");
    }

    @Test
    void unsupportedCurrencyIsRejected() {
        openAccount(newHolder(), "SAVINGS", "EUR")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    // ---- Listing -------------------------------------------------------------------

    @Test
    void accountsAreFilteredByHolderStatusAndNumber() {
        UUID holderId = newHolder();
        AccountBody first = accountOf(openAccount(holderId, "SAVINGS", "HNL"));
        AccountBody second = accountOf(openAccount(holderId, "CHECKING", "USD"));
        changeStatus(second.id(), "SUSPENDED", "Customer request").expectStatus().isOk();

        get(auditor, ACCOUNTS + "?holderId=" + holderId).expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page.totalElements").isEqualTo(2)
                .jsonPath("$.content[0].accountNumber").isEqualTo(first.accountNumber())
                .jsonPath("$.content[1].accountNumber").isEqualTo(second.accountNumber());
        get(auditor, ACCOUNTS + "?holderId=" + holderId + "&status=SUSPENDED").expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].id").isEqualTo(second.id().toString());
        get(auditor, ACCOUNTS + "?accountNumber=" + first.accountNumber()).expectStatus().isOk()
                .expectBody().jsonPath("$.content[0].id").isEqualTo(first.id().toString());
        get(auditor, ACCOUNTS + "?accountNumber=123").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("accountNumber");
    }

    // ---- Balances and movements ----------------------------------------------------

    @Test
    void movementsAppearNewestFirstWithTheRunningBalance() {
        AccountBody account = newAccount("SAVINGS", "HNL");
        UUID ledgerAccount = account.ledgerAccountId();
        deposit(ledgerAccount, "150.00");
        withdraw(ledgerAccount, "50.25");

        get(auditor, ACCOUNTS + "/" + account.id() + "/entries").expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page.totalElements").isEqualTo(2)
                .jsonPath("$.content[0].direction").isEqualTo("DEBIT")
                .jsonPath("$.content[0].amount").isEqualTo("50.25")
                .jsonPath("$.content[0].balanceAfter").isEqualTo("99.75")
                .jsonPath("$.content[0].description").isEqualTo("Test withdrawal")
                .jsonPath("$.content[1].direction").isEqualTo("CREDIT")
                .jsonPath("$.content[1].amount").isEqualTo("150.00")
                .jsonPath("$.content[1].balanceAfter").isEqualTo("150.00");
        get(operator, ACCOUNTS + "/" + account.id() + "/balance").expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ledgerBalance").isEqualTo("99.75")
                .jsonPath("$.availableBalance").isEqualTo("99.75");
    }

    // ---- Status --------------------------------------------------------------------

    @Test
    void accountIsSuspendedReactivatedAndClosedWithItsHistory() {
        AccountBody account = newAccount("SAVINGS", "HNL");
        UUID ledgerAccount = account.ledgerAccountId();

        changeStatus(account.id(), "SUSPENDED", "Fraud investigation").expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("SUSPENDED");
        changeStatus(account.id(), "ACTIVE", "Investigation closed").expectStatus().isOk();
        changeStatus(account.id(), "ACTIVE", "Again").expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_STATUS_TRANSITION");

        deposit(ledgerAccount, "10.00");
        changeStatus(account.id(), "CLOSED", "Customer request").expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.code").isEqualTo("ACCOUNT_HAS_BALANCE");
        get(auditor, ACCOUNTS + "/" + account.id()).expectBody().jsonPath("$.status").isEqualTo("ACTIVE");

        withdraw(ledgerAccount, "10.00");
        changeStatus(account.id(), "CLOSED", "Customer request").expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CLOSED")
                .jsonPath("$.closedAt").isNotEmpty();
        changeStatus(account.id(), "ACTIVE", "Reopen").expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_STATUS_TRANSITION");

        get(auditor, ACCOUNTS + "/" + account.id() + "/status-history").expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[*].toStatus").isEqualTo(List.of("CLOSED", "ACTIVE", "SUSPENDED", "ACTIVE"))
                .jsonPath("$.content[0].reason").isEqualTo("Customer request");
        // Closing the account closed its ledger account: no money can reach it anymore.
        assertThatThrownBy(() -> deposit(ledgerAccount, "1.00"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void reasonIsRequiredToChangeTheStatus() {
        AccountBody account = newAccount("SAVINGS", "HNL");

        changeStatus(account.id(), "SUSPENDED", " ").expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("reason");
    }

    @Test
    void unknownAccountIsNotFound() {
        get(auditor, ACCOUNTS + "/" + UUID.randomUUID()).expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("ACCOUNT_NOT_FOUND");
        changeStatus(UUID.randomUUID(), "SUSPENDED", "Missing").expectStatus().isNotFound();
    }

    // ---- Segregation of duties and the auditor's view -------------------------------

    @Test
    void auditorReadsButCannotManageAndAdministratorHasNoAccess() {
        AccountBody account = newAccount("SAVINGS", "HNL");

        get(auditor, ACCOUNTS + "/" + account.id()).expectStatus().isOk();
        client.put().uri(ACCOUNTS + "/" + account.id() + "/status").headers(h -> h.setBearerAuth(auditor))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "SUSPENDED", "reason", "Not allowed"))
                .exchange()
                .expectStatus().isForbidden();
        get(administrator, ACCOUNTS + "/" + account.id()).expectStatus().isForbidden();
        get(operator, "/api/v1/ledger/verification").expectStatus().isForbidden();
    }

    @Test
    void auditorSeesTheJournalEntryAndAConsistentLedger() {
        AccountBody account = newAccount("SAVINGS", "HNL");
        PostedJournalEntry posted = deposit(account.ledgerAccountId(), "42.00");

        get(auditor, "/api/v1/ledger/journal-entries/" + posted.journalEntryId()).expectStatus().isOk()
                .expectBody()
                .jsonPath("$.lines.length()").isEqualTo(2)
                .jsonPath("$.lines[0].ledgerAccountId").isEqualTo(SystemLedgerAccounts.SETTLEMENT_HNL.toString())
                .jsonPath("$.lines[0].direction").isEqualTo("DEBIT")
                .jsonPath("$.lines[1].direction").isEqualTo("CREDIT")
                .jsonPath("$.lines[1].amount").isEqualTo("42.00");
        get(auditor, "/api/v1/ledger/verification").expectStatus().isOk()
                .expectBody()
                .jsonPath("$.consistent").isEqualTo(true)
                .jsonPath("$.unbalancedJournalEntries.length()").isEqualTo(0)
                .jsonPath("$.accountMismatches.length()").isEqualTo(0);
    }

    // ---- Helpers -------------------------------------------------------------------

    private ResponseSpec registerHolder(String holderType, String fullName, String documentType,
            String documentNumber) {
        return client.post().uri(HOLDERS).headers(h -> h.setBearerAuth(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("holderType", holderType, "fullName", fullName,
                        "documentType", documentType, "documentNumber", documentNumber))
                .exchange();
    }

    private UUID newHolder() {
        return registerHolder("PERSON", "Test Holder", "DNI", randomDni())
                .expectStatus().isCreated()
                .expectBody(HolderBody.class).returnResult().getResponseBody().id();
    }

    private ResponseSpec openAccount(UUID holderId, String type, String currency) {
        return client.post().uri(ACCOUNTS).headers(h -> h.setBearerAuth(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("holderId", holderId, "type", type, "currency", currency))
                .exchange();
    }

    private AccountBody newAccount(String type, String currency) {
        return accountOf(openAccount(newHolder(), type, currency));
    }

    private static AccountBody accountOf(ResponseSpec response) {
        return response.expectStatus().isCreated()
                .expectBody(AccountBody.class).returnResult().getResponseBody();
    }

    private ResponseSpec changeStatus(UUID accountId, String status, String reason) {
        return client.put().uri(ACCOUNTS + "/" + accountId + "/status").headers(h -> h.setBearerAuth(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", status, "reason", reason))
                .exchange();
    }

    private ResponseSpec get(String token, String uri) {
        return client.get().uri(uri).headers(h -> h.setBearerAuth(token)).exchange();
    }

    /** Money in: the settlement account (asset) and the customer account both grow. */
    private PostedJournalEntry deposit(UUID ledgerAccount, String amount) {
        Money money = Money.of(amount, Currency.HNL);
        return ledger.post(new JournalEntryRequest("Test deposit", null, null, List.of(
                JournalLine.debit(SystemLedgerAccounts.SETTLEMENT_HNL, money),
                JournalLine.credit(ledgerAccount, money))));
    }

    private void withdraw(UUID ledgerAccount, String amount) {
        Money money = Money.of(amount, Currency.HNL);
        ledger.post(new JournalEntryRequest("Test withdrawal", null, null, List.of(
                JournalLine.debit(ledgerAccount, money),
                JournalLine.credit(SystemLedgerAccounts.SETTLEMENT_HNL, money))));
    }

    /** A random, well-formed DNI (13 digits), normalized. */
    private static String randomDni() {
        return "0801" + ThreadLocalRandom.current().nextLong(100_000_000L, 1_000_000_000L);
    }

    /** The DNI as people write it: 0801-1990-12345. */
    private static String formatted(String dni) {
        return dni.substring(0, 4) + "-" + dni.substring(4, 8) + "-" + dni.substring(8);
    }

    record HolderBody(UUID id, String holderType, String fullName, String documentType,
            String documentNumber, Instant createdAt) {
    }

    record AccountBody(UUID id, String accountNumber, UUID holderId, String type, String currency,
            String status, UUID ledgerAccountId, Instant openedAt, @Nullable Instant closedAt) {
    }
}
