/**
 * Public error API of the shared module.
 *
 * <p>Modules signal failures by throwing one of the categories of {@link
 * io.github.kenercartagena.tps.shared.error.TpsException} (not found, conflict, business
 * rule violation, authentication failed), each with a module-specific {@link
 * io.github.kenercartagena.tps.shared.error.ErrorCode}.
 */
@NamedInterface("error")
package io.github.kenercartagena.tps.shared.error;

import org.springframework.modulith.NamedInterface;
