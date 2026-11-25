package io.github.kenercartagena.tps.shared.internal;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single source of "now" for the application, always in UTC.
 *
 * <p>Code that needs the current time injects {@link Clock} instead of calling
 * {@code Instant.now()}, so tests can replace it with a fixed clock (token expiry,
 * account lockout, transaction timestamps, ...).
 */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
