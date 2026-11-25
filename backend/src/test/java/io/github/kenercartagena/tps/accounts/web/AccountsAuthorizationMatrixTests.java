package io.github.kenercartagena.tps.accounts.web;

import static io.github.kenercartagena.tps.identity.security.AuthorizationMatrix.row;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import io.github.kenercartagena.tps.accounts.application.AccountBalanceView;
import io.github.kenercartagena.tps.accounts.application.AccountHolderService;
import io.github.kenercartagena.tps.accounts.application.AccountHolderView;
import io.github.kenercartagena.tps.accounts.application.AccountService;
import io.github.kenercartagena.tps.accounts.application.AccountView;
import io.github.kenercartagena.tps.accounts.domain.AccountStatus;
import io.github.kenercartagena.tps.accounts.domain.AccountType;
import io.github.kenercartagena.tps.accounts.domain.DocumentType;
import io.github.kenercartagena.tps.accounts.domain.HolderType;
import io.github.kenercartagena.tps.identity.security.AuthorizationMatrix;
import io.github.kenercartagena.tps.identity.security.WebSecurityTestConfiguration;
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
 * Who may call what on account holders and accounts. Operators manage, auditors read,
 * administrators have no access (segregation of duties). Services are mocked, so this
 * checks authorization only. Mirrors the table in {@code docs/security.md}.
 */
@WebMvcTest(controllers = {AccountHolderController.class, AccountController.class})
@Import(WebSecurityTestConfiguration.class)
class AccountsAuthorizationMatrixTests {

    private static final UUID ID = UUID.fromString("0193a1b2-0000-7000-8000-0000000000d1");

    private static final String REGISTER_HOLDER = """
            {"holderType": "PERSON", "fullName": "María López",
             "documentType": "DNI", "documentNumber": "0801-1990-12345"}""";
    private static final String OPEN_ACCOUNT = """
            {"holderId": "%s", "type": "SAVINGS", "currency": "HNL"}""".formatted(ID);
    private static final String CHANGE_STATUS = """
            {"status": "SUSPENDED", "reason": "Fraud investigation"}""";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AccountHolderService holders;

    @MockitoBean
    private AccountService accounts;

    @BeforeEach
    void stubServices() {
        Instant now = Instant.now();
        AccountHolderView holder = new AccountHolderView(ID, HolderType.PERSON, "María López",
                DocumentType.DNI, "0801199012345", now, now);
        AccountView account = new AccountView(ID, "100000000093", ID, AccountType.SAVINGS,
                Currency.HNL, AccountStatus.ACTIVE, ID, now, now, null);
        given(holders.register(any(), any())).willReturn(holder);
        given(holders.get(any())).willReturn(holder);
        given(holders.list(any(), anyInt(), anyInt())).willReturn(new PageImpl<>(List.of(holder)));
        given(accounts.open(any(), any())).willReturn(account);
        given(accounts.get(any())).willReturn(account);
        given(accounts.list(any(), anyInt(), anyInt())).willReturn(new PageImpl<>(List.of(account)));
        given(accounts.changeStatus(any(), any(), anyString(), any())).willReturn(account);
        given(accounts.balance(any())).willReturn(new AccountBalanceView(ID, "100000000093",
                Currency.HNL, "0.00", "0.00", now));
        given(accounts.entries(any(), anyInt(), anyInt())).willReturn(new PageImpl<>(List.of()));
        given(accounts.statusHistory(any(), anyInt(), anyInt())).willReturn(new PageImpl<>(List.of()));
    }

    /** Columns: anonymous, SYSTEM_ADMIN, OPERATOR, AUDITOR. */
    static Stream<Arguments> matrix() {
        String holder = "/api/v1/account-holders/" + ID;
        String account = "/api/v1/accounts/" + ID;
        return Stream.of(
                row(HttpMethod.POST, "/api/v1/account-holders", REGISTER_HOLDER, 401, 403, 201, 403),
                row(HttpMethod.GET, "/api/v1/account-holders", null, 401, 403, 200, 200),
                row(HttpMethod.GET, holder, null, 401, 403, 200, 200),
                row(HttpMethod.POST, "/api/v1/accounts", OPEN_ACCOUNT, 401, 403, 201, 403),
                row(HttpMethod.GET, "/api/v1/accounts", null, 401, 403, 200, 200),
                row(HttpMethod.GET, account, null, 401, 403, 200, 200),
                row(HttpMethod.PUT, account + "/status", CHANGE_STATUS, 401, 403, 200, 403),
                row(HttpMethod.GET, account + "/balance", null, 401, 403, 200, 200),
                row(HttpMethod.GET, account + "/entries", null, 401, 403, 200, 200),
                row(HttpMethod.GET, account + "/status-history", null, 401, 403, 200, 200))
                .flatMap(rows -> rows);
    }

    @ParameterizedTest(name = "{0} {1} as {3} -> {4}")
    @MethodSource("matrix")
    void endpointEnforcesItsPermission(HttpMethod method, String path, @Nullable String body,
            String caller, int expectedStatus) {
        AuthorizationMatrix.assertCall(mvc, method, path, body, caller, expectedStatus);
    }
}
