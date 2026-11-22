package io.github.kenercartagena.tps;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a real PostgreSQL container for tests and wires it into Spring via
 * {@link ServiceConnection}, so tests never depend on a locally installed database.
 *
 * <p>The image version must stay aligned with {@code docker-compose.yml}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }
}
