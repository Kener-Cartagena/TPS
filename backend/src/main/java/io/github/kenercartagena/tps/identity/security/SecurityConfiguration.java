package io.github.kenercartagena.tps.identity.security;

import io.github.kenercartagena.tps.shared.web.ApiPaths;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * HTTP security of the whole application (ADR 0005).
 *
 * <ul>
 *   <li><strong>Stateless</strong>: no HTTP session; every request carries
 *       {@code Authorization: Bearer <JWT>}, verified by Spring Security's OAuth 2.0
 *       resource server support.</li>
 *   <li><strong>Deny by default</strong>: only login, health/info and the API documentation
 *       are public; everything else requires a valid token. Unknown URLs therefore answer
 *       {@code 401} to anonymous callers, which does not reveal which endpoints exist.</li>
 *   <li><strong>Permissions per operation</strong>: controllers check permissions with
 *       {@code @PreAuthorize} (method security), so the rule sits next to the code it
 *       protects.</li>
 *   <li><strong>CSRF disabled</strong>: CSRF abuses credentials the browser sends on its own
 *       (cookies). A bearer token is added explicitly by the client, so it does not apply.
 *       To be revisited if the dashboard (Phase 10) uses cookies.</li>
 * </ul>
 *
 * <p>Spring Security's default response headers stay on: {@code X-Content-Type-Options},
 * {@code X-Frame-Options: DENY}, {@code Cache-Control: no-store}, HSTS on HTTPS.
 *
 * <p>Spring Security 7 also publishes OAuth 2.0 Protected Resource Metadata (RFC 9728) at
 * {@code /.well-known/oauth-protected-resource}. It cannot be switched off, so it is
 * corrected to describe TPS truthfully: tokens in the {@code Authorization} header, not
 * bound to TLS client certificates.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfiguration {

    static final String LOGIN_PATH = ApiPaths.API_V1 + "/auth/tokens";

    private static final String[] PUBLIC_OPERATIONS = {
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
            ProblemDetailsSecurityHandler problemDetailsHandler) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        // Container error pages (e.g. a malformed URL rejected by Tomcat).
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, LOGIN_PATH).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_OPERATIONS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtAuthoritiesConverter()))
                        .protectedResourceMetadata(metadata -> metadata.protectedResourceMetadataCustomizer(
                                builder -> builder.resourceName("TPS API")
                                        .tlsClientCertificateBoundAccessTokens(false)))
                        .authenticationEntryPoint(problemDetailsHandler)
                        .accessDeniedHandler(problemDetailsHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemDetailsHandler)
                        .accessDeniedHandler(problemDetailsHandler));
        return http.build();
    }

    /**
     * Hashes are stored with their algorithm, e.g. {@code {bcrypt}$2a$10$...}. New
     * passwords use the current default (bcrypt); if the default changes, existing hashes
     * keep working and can be upgraded on the next login.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
