package io.github.kenercartagena.tps;

import org.springframework.boot.SpringApplication;

/**
 * Runs the application locally backed by a throwaway Testcontainers PostgreSQL
 * instance — an alternative to {@code docker compose up} for quick experiments.
 */
public class TestTpsApplication {

    public static void main(String[] args) {
        SpringApplication.from(TpsApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
