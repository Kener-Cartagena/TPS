/**
 * Shared module: cross-cutting building blocks used by every business module.
 *
 * <ul>
 *   <li>{@code shared.error} (public) — exception categories and error codes that
 *       modules throw; they are translated into RFC 9457 Problem Details responses.</li>
 *   <li>{@code shared.web} (public) — REST API conventions such as base paths.</li>
 *   <li>{@code shared.internal} (private) — the global exception handler, request id
 *       logging, the application {@link java.time.Clock} and OpenAPI configuration.
 *       Other modules must not depend on it.</li>
 * </ul>
 *
 * <p>Keep this module small: business logic never belongs here.
 */
@ApplicationModule(displayName = "Shared")
package io.github.kenercartagena.tps.shared;

import org.springframework.modulith.ApplicationModule;
