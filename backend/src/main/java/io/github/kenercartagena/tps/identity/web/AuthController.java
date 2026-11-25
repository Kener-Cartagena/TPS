package io.github.kenercartagena.tps.identity.web;

import io.github.kenercartagena.tps.identity.security.AuthenticationService;
import io.github.kenercartagena.tps.shared.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login. A token is not a resource that can be fetched later, so the response is
 * {@code 200 OK} without a {@code Location} header.
 */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/auth")
@Tag(name = "Authentication")
class AuthController {

    private final AuthenticationService authenticationService;

    AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/tokens")
    @SecurityRequirements // public: no token is needed to get a token
    @Operation(summary = "Log in", description = """
            Exchanges a username and password for an access token (JWT). Any failure returns \
            401 INVALID_CREDENTIALS without saying why. Too many consecutive failures lock \
            the account temporarily.""")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(authenticationService.login(request.username(), request.password()));
    }
}
