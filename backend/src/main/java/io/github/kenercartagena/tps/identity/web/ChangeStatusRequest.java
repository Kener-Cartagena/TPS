package io.github.kenercartagena.tps.identity.web;

import io.github.kenercartagena.tps.identity.domain.UserStatus;
import jakarta.validation.constraints.NotNull;

/** Body of {@code PUT /api/v1/users/{userId}/status}. {@code ACTIVE} also lifts a lockout. */
record ChangeStatusRequest(@NotNull UserStatus status) {
}
