package io.github.kenercartagena.tps.identity.web;

import io.github.kenercartagena.tps.identity.CurrentUser;
import io.github.kenercartagena.tps.identity.Permissions;
import io.github.kenercartagena.tps.identity.application.UserService;
import io.github.kenercartagena.tps.identity.application.UserView;
import io.github.kenercartagena.tps.shared.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * User management and the caller's own profile. Every operation requires a valid token;
 * administrative operations also require a permission ({@code @PreAuthorize}).
 */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/users")
@Tag(name = "Users")
class UserController {

    private static final String CAN_READ = "hasAuthority('" + Permissions.USER_READ + "')";
    private static final String CAN_MANAGE = "hasAuthority('" + Permissions.USER_MANAGE + "')";

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    // ---- The caller's own account --------------------------------------------------

    @GetMapping("/me")
    @Operation(summary = "Get my profile")
    public UserView me(@AuthenticationPrincipal Jwt token) {
        return userService.get(CurrentUser.id(token));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Change my password", description = "Requires the current password.")
    public ResponseEntity<Void> changeMyPassword(@AuthenticationPrincipal Jwt token,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changeOwnPassword(CurrentUser.id(token), request.currentPassword(),
                request.newPassword());
        return ResponseEntity.noContent().build();
    }

    // ---- Administration ------------------------------------------------------------

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Create a user", description = "Requires USER_MANAGE.")
    public ResponseEntity<UserView> create(@AuthenticationPrincipal Jwt token,
            @Valid @RequestBody CreateUserRequest request) {
        UserView user = userService.create(request.toCommand(), CurrentUser.id(token));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{userId}").buildAndExpand(user.id()).toUri();
        return ResponseEntity.created(location).body(user);
    }

    @GetMapping
    @PreAuthorize(CAN_READ)
    @Operation(summary = "List users", description = "Sorted by username. Requires USER_READ.")
    public PagedModel<UserView> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(userService.list(page, size));
    }

    @GetMapping("/{userId}")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Get a user", description = "Requires USER_READ.")
    public UserView get(@PathVariable UUID userId) {
        return userService.get(userId);
    }

    @PutMapping("/{userId}/role")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Change a user's role", description = """
            Requires USER_MANAGE. Not allowed on yourself, nor if it would leave no active \
            administrator. Takes effect at the user's next login.""")
    public UserView changeRole(@AuthenticationPrincipal Jwt token, @PathVariable UUID userId,
            @Valid @RequestBody ChangeRoleRequest request) {
        return userService.changeRole(userId, request.role(), CurrentUser.id(token));
    }

    @PutMapping("/{userId}/status")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Activate or disable a user", description = """
            Requires USER_MANAGE. ACTIVE also unlocks a temporarily locked account. Not \
            allowed on yourself, nor if it would leave no active administrator.""")
    public UserView changeStatus(@AuthenticationPrincipal Jwt token, @PathVariable UUID userId,
            @Valid @RequestBody ChangeStatusRequest request) {
        return userService.changeStatus(userId, request.status(), CurrentUser.id(token));
    }
}
