package io.github.kenercartagena.tps.identity.web;

import io.github.kenercartagena.tps.identity.domain.Role;
import jakarta.validation.constraints.NotNull;

/** Body of {@code PUT /api/v1/users/{userId}/role}. */
record ChangeRoleRequest(@NotNull Role role) {
}
