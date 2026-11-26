package io.github.kenercartagena.tps.transactions.web;

import static io.github.kenercartagena.tps.identity.security.AuthorizationMatrix.row;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

import io.github.kenercartagena.tps.identity.security.AuthorizationMatrix;
import io.github.kenercartagena.tps.identity.security.WebSecurityTestConfiguration;
import io.github.kenercartagena.tps.shared.money.Currency;
import io.github.kenercartagena.tps.transactions.application.TransactionEngine;
import io.github.kenercartagena.tps.transactions.application.TransactionOutcome;
import io.github.kenercartagena.tps.transactions.application.TransactionQueries;
import io.github.kenercartagena.tps.transactions.application.TransactionView;
import io.github.kenercartagena.tps.transactions.domain.TransactionStatus;
import io.github.kenercartagena.tps.transactions.domain.TransactionType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
 * Who may call what on transactions. Operators deposit, withdraw and read; auditors only
 * read; administrators have no access (segregation of duties). The engine is mocked, so
 * this checks authorization only. Mirrors the table in {@code docs/security.md}.
 *
 * <p>Every call carries an {@code Idempotency-Key}: deposits and withdrawals require it,
 * and the other endpoints ignore it.
 */
@WebMvcTest(controllers = {CashOperationController.class, TransactionController.class})
@Import(WebSecurityTestConfiguration.class)
class TransactionsAuthorizationMatrixTests {

    private static final UUID ID = UUID.fromString("0193a1b2-0000-7000-8000-0000000000e1");

    private static final String CASH = """
            {"accountId": "%s", "amount": "10.00", "currency": "HNL"}""".formatted(ID);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TransactionEngine engine;

    @MockitoBean
    private TransactionQueries queries;

    @BeforeEach
    void stubServices() {
        Instant now = Instant.now();
        TransactionView transaction = new TransactionView(ID, TransactionType.DEPOSIT,
                TransactionStatus.COMPLETED, "10.00", Currency.HNL, null, ID, null, ID, null, null, ID,
                now, now, now);
        given(engine.submit(any(), any(), any())).willReturn(new TransactionOutcome(transaction, false));
        given(queries.get(any())).willReturn(transaction);
        given(queries.list(any(), anyInt(), anyInt())).willReturn(new PageImpl<>(List.of(transaction)));
        given(queries.statusHistory(any())).willReturn(List.of());
    }

    /** Columns: anonymous, SYSTEM_ADMIN, OPERATOR, AUDITOR. */
    static Stream<Arguments> matrix() {
        String transaction = "/api/v1/transactions/" + ID;
        return Stream.of(
                row(HttpMethod.POST, "/api/v1/deposits", CASH, 401, 403, 201, 403),
                row(HttpMethod.POST, "/api/v1/withdrawals", CASH, 401, 403, 201, 403),
                row(HttpMethod.GET, "/api/v1/transactions", null, 401, 403, 200, 200),
                row(HttpMethod.GET, transaction, null, 401, 403, 200, 200),
                row(HttpMethod.GET, transaction + "/status-history", null, 401, 403, 200, 200))
                .flatMap(rows -> rows);
    }

    @ParameterizedTest(name = "{0} {1} as {3} -> {4}")
    @MethodSource("matrix")
    void endpointEnforcesItsPermission(HttpMethod method, String path, @Nullable String body,
            String caller, int expectedStatus) {
        AuthorizationMatrix.assertCall(mvc, method, path, body,
                Map.of("Idempotency-Key", "matrix-test-key"), caller, expectedStatus);
    }
}
