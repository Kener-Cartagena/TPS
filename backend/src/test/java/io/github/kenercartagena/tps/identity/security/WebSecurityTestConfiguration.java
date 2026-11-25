package io.github.kenercartagena.tps.identity.security;

import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * The real TPS web security (filter chain, JWT verification, error responses) for
 * {@code @WebMvcTest} slices, which do not load {@code @Configuration} classes on their
 * own. It lives in this package because those classes are package-private.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({SecurityConfiguration.class, JwtConfiguration.class, ProblemDetailsSecurityHandler.class})
public class WebSecurityTestConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
