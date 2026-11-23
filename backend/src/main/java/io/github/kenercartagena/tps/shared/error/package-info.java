/**
 * Public error API of the shared module.
 *
 * <p>Modules signal failures by throwing (subclasses of) {@link
 * io.github.kenercartagena.tps.shared.error.ResourceNotFoundException} or {@link
 * io.github.kenercartagena.tps.shared.error.BusinessRuleViolationException}, each with a
 * module-specific {@link io.github.kenercartagena.tps.shared.error.ErrorCode}.
 */
@NamedInterface("error")
package io.github.kenercartagena.tps.shared.error;

import org.springframework.modulith.NamedInterface;
