/**
 * Identity and Access module: users, authentication and authorization.
 *
 * <ul>
 *   <li>{@code identity} (public) — {@link io.github.kenercartagena.tps.identity.Permissions},
 *       the permission names other modules use to protect their operations with
 *       {@code @PreAuthorize}.</li>
 *   <li>{@code identity.domain} (internal) — the {@code User} aggregate, roles and their
 *       permissions, persistence.</li>
 *   <li>{@code identity.application} (internal) — user management use cases.</li>
 *   <li>{@code identity.security} (internal) — Spring Security configuration, login and
 *       JWT issuing/validation.</li>
 *   <li>{@code identity.web} (internal) — REST controllers and request/response bodies.</li>
 * </ul>
 *
 * <p>See {@code docs/security.md} and {@code docs/adr/0005-authentication-and-authorization.md}.
 */
@ApplicationModule(displayName = "Identity and Access")
package io.github.kenercartagena.tps.identity;

import org.springframework.modulith.ApplicationModule;
