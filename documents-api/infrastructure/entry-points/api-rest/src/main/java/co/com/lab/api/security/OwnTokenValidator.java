package co.com.lab.api.security;

import co.com.lab.model.exception.InvalidPrincipalException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reglas propias sobre el JWT: access token, para esta API, y con claims que forman un Principal valido
 * (un rol fuera del vocabulario hace que se rechace en vez de inventar una tupla).
 */
public class OwnTokenValidator implements OAuth2TokenValidator<Jwt> {

    private final String audience;

    public OwnTokenValidator(String audience) {
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (jwt.getAudience() == null || !jwt.getAudience().contains(audience)) {
            return fail("El token no es para " + audience);
        }
        if (!"access".equals(jwt.getClaimAsString("token_use"))) {
            return fail("token_use debe ser access");
        }
        try {
            JwtPrincipalMapper.toPrincipal(jwt);
        } catch (InvalidPrincipalException e) {
            return fail(e.getMessage());
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult fail(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}
