package io.github.kenercartagena.tps;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the OpenAPI document of the running application to
 * {@code target/openapi/openapi.json}, in a stable form: keys sorted, LF line endings, and
 * without what changes on every run or release (the server URL with its random port, the
 * application version).
 *
 * <p>The CI job "API contract" compares this file with the reviewed snapshot in
 * {@code docs/api/openapi.json} and fails if they differ: a change to the public API
 * (a new endpoint, a renamed field, a removed error code) must be committed on purpose,
 * where reviewers see it. To update the snapshot after a deliberate change:
 *
 * <pre>
 *   cd backend &amp;&amp; ./mvnw verify            # or only this test
 *   cd .. &amp;&amp; UPDATE=true bash .github/scripts/check-api-contract.sh
 * </pre>
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class ApiContractTests {

    static final Path OUTPUT = Path.of("target", "openapi", "openapi.json");

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    @Autowired
    private RestTestClient client;

    @Test
    void writesTheOpenApiDocument() throws IOException {
        String body = client.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();

        Map<String, Object> document = normalized(JSON.readValue(body, Map.class));

        assertThat(document).containsKeys("openapi", "info", "paths", "components");
        @SuppressWarnings("unchecked")
        Map<String, Object> paths = (Map<String, Object>) document.get("paths");
        assertThat(paths).containsKeys("/api/v1/deposits", "/api/v1/withdrawals",
                "/api/v1/transactions", "/api/v1/accounts");

        Files.createDirectories(OUTPUT.getParent());
        String json = JSON.writeValueAsString(document).replace("\r\n", "\n") + "\n";
        Files.writeString(OUTPUT, json, StandardCharsets.UTF_8);
    }

    /** Sorted keys, and none of the values that change from one run or release to the next. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalized(Map<String, Object> document) {
        Map<String, Object> sorted = (Map<String, Object>) sortKeys(document);
        sorted.remove("servers");
        Map<String, Object> info = (Map<String, Object>) sorted.get("info");
        info.put("version", "not part of the contract");
        return sorted;
    }

    @SuppressWarnings("unchecked")
    private static Object sortKeys(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, child) -> sorted.put((String) key, sortKeys(child)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(child -> copy.add(sortKeys(child)));
            return copy;
        }
        return value;
    }
}
