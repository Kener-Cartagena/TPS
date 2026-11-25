package io.github.kenercartagena.tps.identity;

import io.github.kenercartagena.tps.identity.domain.Role;
import io.github.kenercartagena.tps.identity.security.SecurityProperties;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Real access tokens for end-to-end tests of other modules: logs in as the bootstrap
 * administrator, creates a user with the requested role through the API, and logs in as
 * that user. Each call creates a new user with a unique name.
 */
public final class TestUsers {

    private static final String PASSWORD = "a-long-password-1";

    private final RestTestClient client;
    private final SecurityProperties securityProperties;

    public TestUsers(RestTestClient client, SecurityProperties securityProperties) {
        this.client = client;
        this.securityProperties = securityProperties;
    }

    /** A token of the bootstrap administrator. */
    public String administratorToken() {
        SecurityProperties.Admin admin = securityProperties.bootstrap().admin();
        return login(admin.username(), admin.password());
    }

    /** A token of a new user with the role. */
    public String tokenFor(Role role) {
        String username = "user-" + UUID.randomUUID().toString().substring(0, 8);
        client.post().uri("/api/v1/users").headers(h -> h.setBearerAuth(administratorToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "fullName", "Test " + role,
                        "password", PASSWORD, "role", role.name()))
                .exchange()
                .expectStatus().isCreated();
        return login(username, PASSWORD);
    }

    private String login(String username, String password) {
        return client.post().uri("/api/v1/auth/tokens").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", password))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Token.class).returnResult().getResponseBody().accessToken();
    }

    private record Token(String accessToken) {
    }
}
