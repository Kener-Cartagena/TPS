package io.github.kenercartagena.tps.ledger.web;

import static io.github.kenercartagena.tps.identity.security.AuthorizationMatrix.row;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

import io.github.kenercartagena.tps.identity.security.AuthorizationMatrix;
import io.github.kenercartagena.tps.identity.security.WebSecurityTestConfiguration;
import io.github.kenercartagena.tps.ledger.LedgerAccountType;
import io.github.kenercartagena.tps.ledger.application.JournalEntryView;
import io.github.kenercartagena.tps.ledger.application.LedgerAccountView;
import io.github.kenercartagena.tps.ledger.application.LedgerQueries;
import io.github.kenercartagena.tps.ledger.application.LedgerVerificationReport;
import io.github.kenercartagena.tps.ledger.application.LedgerVerifier;
import io.github.kenercartagena.tps.ledger.domain.LedgerAccountStatus;
import io.github.kenercartagena.tps.shared.money.Currency;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Who may read the ledger: only auditors ({@code LEDGER_READ}). Services are mocked, so
 * this checks authorization only. Mirrors the table in {@code docs/security.md}.
 */
@WebMvcTest(controllers = LedgerController.class)
@Import(WebSecurityTestConfiguration.class)
class LedgerAuthorizationMatrixTests {

    private static final UUID ID = UUID.fromString("0193a1b2-0000-7000-8000-0000000000c1");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private LedgerQueries queries;

    @MockitoBean
    private LedgerVerifier verifier;

    @BeforeEach
    void stubServices() {
        LedgerAccountView account = new LedgerAccountView(ID, "SETTLEMENT-HNL", "Settlement",
                LedgerAccountType.ASSET, Currency.HNL, "0.00", false, LedgerAccountStatus.OPEN,
                Instant.now(), null);
        given(queries.listAccounts(anyInt(), anyInt())).willReturn(new PageImpl<>(List.of(account)));
        given(queries.getAccount(any())).willReturn(account);
        given(queries.postings(any(), anyInt(), anyInt())).willReturn(new PageImpl<>(List.of()));
        given(queries.getJournalEntry(any())).willReturn(new JournalEntryView(ID, "Entry", null,
                Currency.HNL, Instant.now(), null, List.of()));
        given(verifier.verify()).willReturn(new LedgerVerificationReport(Instant.now(), true, 0, 2,
                List.of(), List.of(), List.of()));
    }

    /** Columns: anonymous, SYSTEM_ADMIN, OPERATOR, AUDITOR. */
    static Stream<Arguments> matrix() {
        return Stream.of(
                row(HttpMethod.GET, "/api/v1/ledger/accounts", null, 401, 403, 403, 200),
                row(HttpMethod.GET, "/api/v1/ledger/accounts/" + ID, null, 401, 403, 403, 200),
                row(HttpMethod.GET, "/api/v1/ledger/accounts/" + ID + "/postings", null, 401, 403, 403, 200),
                row(HttpMethod.GET, "/api/v1/ledger/journal-entries/" + ID, null, 401, 403, 403, 200),
                row(HttpMethod.GET, "/api/v1/ledger/verification", null, 401, 403, 403, 200))
                .flatMap(rows -> rows);
    }

    @ParameterizedTest(name = "{0} {1} as {3} -> {4}")
    @MethodSource("matrix")
    void endpointEnforcesItsPermission(HttpMethod method, String path, @Nullable String body,
            String caller, int expectedStatus) {
        AuthorizationMatrix.assertCall(mvc, method, path, body, caller, expectedStatus);
    }
}
