package io.github.kenercartagena.tps;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * End-to-end checks of the API foundation against a running server (random port) and a
 * real PostgreSQL database: health probes, build info, OpenAPI document and the error
 * format. Every call here is anonymous: these endpoints must stay public, and everything
 * else must be closed to anonymous callers.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class ApiFoundationIntegrationTests {

    @Autowired
    private RestTestClient client;

    @Test
    void healthIsUp() {
        client.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void livenessProbeIsUp() {
        client.get().uri("/actuator/health/liveness").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void readinessProbeIsUpAndChecksTheDatabase() {
        client.get().uri("/actuator/health/readiness").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void infoExposesBuildVersion() {
        client.get().uri("/actuator/info").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.build.name").isEqualTo("tps")
                .jsonPath("$.build.version").isNotEmpty();
    }

    /** Published by Spring Security (RFC 9728); it must describe TPS truthfully. */
    @Test
    void protectedResourceMetadataDescribesBearerTokensInTheHeader() {
        client.get().uri("/.well-known/oauth-protected-resource").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.resource_name").isEqualTo("TPS API")
                .jsonPath("$.bearer_methods_supported[0]").isEqualTo("header")
                .jsonPath("$.tls_client_certificate_bound_access_tokens").isEqualTo(false);
    }

    /** Not exposed at all, and also closed to anonymous callers (deny by default). */
    @Test
    void sensitiveActuatorEndpointsAreNotPublic() {
        client.get().uri("/actuator/env").exchange().expectStatus().isUnauthorized();
        client.get().uri("/actuator/beans").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void openApiDocumentIsPublishedWithBearerAuthentication() {
        client.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.openapi").isNotEmpty()
                .jsonPath("$.info.title").isEqualTo("TPS API")
                .jsonPath("$.components.securitySchemes.bearerAuth.scheme").isEqualTo("bearer");
    }

    @Test
    void swaggerUiIsAvailable() {
        client.get().uri("/swagger-ui/index.html").exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    /**
     * An anonymous caller cannot tell whether an endpoint exists: unknown URLs also answer
     * 401, in the standard error format. (Authenticated callers get 404, see the identity
     * integration tests.)
     */
    @Test
    void unknownEndpointRequiresAuthenticationAndReturnsProblemDetails() {
        client.get().uri("/api/v1/does-not-exist").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Request-Id")
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED")
                .jsonPath("$.requestId").isNotEmpty()
                .jsonPath("$.instance").isEqualTo("/api/v1/does-not-exist");
    }
}
