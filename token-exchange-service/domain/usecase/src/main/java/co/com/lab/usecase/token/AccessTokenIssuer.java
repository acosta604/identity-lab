package co.com.lab.usecase.token;

import co.com.lab.model.accesscontext.AccessContext;
import co.com.lab.model.exception.BusinessException;
import co.com.lab.model.exception.ErrorType;
import co.com.lab.model.token.AccessTokenRequest;
import co.com.lab.model.token.Actor;
import co.com.lab.model.token.IssuedAccessToken;
import co.com.lab.model.token.TokenPolicy;
import co.com.lab.model.token.gateways.AccessTokenGateway;
import co.com.lab.model.token.gateways.AudiencePolicyGateway;
import co.com.lab.model.user.User;

import java.time.Duration;

/** Regla comun de emision: la audiencia debe estar permitida y la vida es la menor de las dos politicas. */
public final class AccessTokenIssuer {

    private final AccessTokenGateway tokens;
    private final AudiencePolicyGateway audiences;
    private final TokenPolicy policy;

    public AccessTokenIssuer(AccessTokenGateway tokens, AudiencePolicyGateway audiences, TokenPolicy policy) {
        this.tokens = tokens;
        this.audiences = audiences;
        this.policy = policy;
    }

    public String resolveAudience(String requested) {
        String audience = requested == null || requested.isBlank() ? policy.defaultAudience() : requested;
        ttlFor(audience);
        return audience;
    }

    public Duration ttlFor(String audience) {
        Duration max = audiences.maxTtl(audience)
                .orElseThrow(() -> new BusinessException(ErrorType.INVALID_TARGET, "Audiencia no permitida: " + audience));
        return policy.effectiveTtl(max);
    }

    public IssuedAccessToken issue(User user, AccessContext context, String audience, String idp,
                                   String sessionId, Actor actor) {
        return tokens.issue(new AccessTokenRequest(user, context, audience, idp, sessionId, ttlFor(audience), actor));
    }

    public String defaultAudience() {
        return policy.defaultAudience();
    }
}
