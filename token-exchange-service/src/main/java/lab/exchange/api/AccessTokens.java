package lab.exchange.api;

import lab.exchange.claims.ClaimsBuilder;
import lab.exchange.claims.OrgContext;
import lab.exchange.claims.UserInfo;
import lab.exchange.config.LabProperties;
import lab.exchange.error.ExchangeException;
import lab.exchange.identity.AudienceRegistry;
import lab.exchange.signing.TokenSigner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/** Emite el JWT propio para una audiencia permitida. */
@Component
public class AccessTokens {

    private final ClaimsBuilder claims;
    private final TokenSigner signer;
    private final AudienceRegistry audiences;
    private final LabProperties props;

    public AccessTokens(ClaimsBuilder claims, TokenSigner signer, AudienceRegistry audiences, LabProperties props) {
        this.claims = claims;
        this.signer = signer;
        this.audiences = audiences;
        this.props = props;
    }

    public record Issued(String token, Duration ttl) {
    }

    public Duration ttlFor(String audience) {
        Duration max = audiences.maxTtl(audience)
                .orElseThrow(() -> ExchangeException.invalidTarget("Audiencia no permitida: " + audience));
        return max.compareTo(props.accessTokenTtl()) < 0 ? max : props.accessTokenTtl();
    }

    public Issued issue(UserInfo user, OrgContext context, String audience, String idp, String sessionId,
                        Map<String, Object> actor) {
        Duration ttl = ttlFor(audience);
        var set = claims.build(new ClaimsBuilder.Request(user, context, audience, idp, sessionId, ttl, actor));
        return new Issued(signer.sign(set), ttl);
    }
}
