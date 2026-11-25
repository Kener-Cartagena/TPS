package io.github.kenercartagena.tps.identity.security;

import io.github.kenercartagena.tps.identity.domain.Role;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Turns a verified JWT into the authenticated principal of the request.
 *
 * <p>The token only carries the role; its permissions are looked up here, so the
 * role-to-permission mapping lives in one place ({@link Role}). The authorities are
 * {@code ROLE_<role>} plus every permission of the role, and the principal name is the
 * user id ({@code sub}).
 */
public class JwtAuthoritiesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authoritiesOf(roleOf(jwt)), jwt.getSubject());
    }

    /** The authorities granted to a role. Also used by tests to build matching tokens. */
    public static Collection<GrantedAuthority> authoritiesOf(Role role) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        role.permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        return List.copyOf(authorities);
    }

    /** A signed token without a known role is rejected (401) rather than granted nothing. */
    private static Role roleOf(Jwt jwt) {
        String role = jwt.getClaimAsString(JwtTokenIssuer.ROLE_CLAIM);
        if (role == null || jwt.getSubject() == null) {
            throw new InvalidBearerTokenException("The token has no subject or role.");
        }
        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException ex) {
            throw new InvalidBearerTokenException("The token has an unknown role.", ex);
        }
    }
}
