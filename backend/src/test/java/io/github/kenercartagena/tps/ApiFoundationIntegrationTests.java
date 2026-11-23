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
 * format of an unknown endpoint.
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

    @Test
    void sensitiveActuatorEndpointsAreNotExposed() {
        client.get().uri("/actuator/env").exchange().expectStatus().isNotFound();
        client.get().uri("/actuator/beans").exchange().expectStatus().isNotFound();
    }

    @Test
    void openApiDocumentIsPublished() {
        client.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.openapi").isNotEmpty()
                .jsonPath("$.info.title").isEqualTo("TPS API");
    }

    @Test
    void swaggerUiIsAvailable() {
        client.get().uri("/swagger-ui/index.html").exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    void unknownEndpointReturnsProblemDetailsWithRequestId() {
        client.get().uri("/api/v1/does-not-exist").exchange()
                .expectStatus().isNotFound()
                .expectHeader().exists("X-Request-Id")
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("NOT_FOUND")
                .jsonPath("$.requestId").isNotEmpty()
                .jsonPath("$.instance").isEqualTo("/api/v1/does-not-exist");
    }
}
