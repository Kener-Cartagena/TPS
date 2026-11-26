package io.github.kenercartagena.tps.shared.internal;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single source of "now" for the application, always in UTC.
 *
 * <p>Code that needs the current time injects {@link Clock} instead of calling
 * {@code Instant.now()}, so tests can replace it with a fixed clock (token expiry,
 * account lockout, transaction timestamps, ...).
 *
 * <p>It ticks in whole <strong>microseconds</strong>, the precision of PostgreSQL's
 * {@code TIMESTAMPTZ}. The system clock can be finer (100 ns on Windows, nanoseconds on
 * Linux), and the database would silently round such a value: the response to a request
 * would then show a timestamp that was never stored, and the same record read back later
 * would differ from it (e.g. an idempotent replay, which must return the same body).
 */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

    /** Precision of PostgreSQL timestamps. */
    static final Duration DATABASE_PRECISION = Duration.ofNanos(1_000);

    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), DATABASE_PRECISION);
    }
}
