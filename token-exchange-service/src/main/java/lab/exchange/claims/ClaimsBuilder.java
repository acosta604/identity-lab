package lab.exchange.claims;

import com.nimbusds.jwt.JWTClaimsSet;
import lab.exchange.config.LabProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Arma los claims propios. Los claims describen el contexto de la persona (quien es, en que tenant,
 * organizacion y equipos, con que rol); nunca permisos sobre recursos: eso lo decide OpenFGA.
 */
@Component
public class ClaimsBuilder {

    private final LabProperties props;
    private final Clock clock;

    public ClaimsBuilder(LabProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    public record Request(
            UserInfo user,
            OrgContext context,
            String audience,
            String idp,
            String sessionId,
            Duration ttl,
            Map<String, Object> actor) {
    }

    public JWTClaimsSet build(Request r) {
        Instant now = clock.instant();

        Map<String, Object> usr = new LinkedHashMap<>();
        usr.put("name", r.user().name());
        usr.put("email", r.user().email());

        Map<String, Object> org = new LinkedHashMap<>();
        org.put("id", r.context().orgId());
        org.put("role", r.context().orgRole());

        List<Map<String, Object>> teams = r.context().teams().stream()
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.id());
                    m.put("role", t.role());
                    return m;
                })
                .toList();

        JWTClaimsSet.Builder b = new JWTClaimsSet.Builder()
                .issuer(props.issuer())
                .subject(r.user().id())
                .audience(r.audience())
                .claim(ClaimNames.USER, usr)
                .claim(ClaimNames.TENANT, r.context().tenantId())
                .claim(ClaimNames.ORGANIZATION, org)
                .claim(ClaimNames.TEAMS, teams)
                .claim(ClaimNames.ROLES, List.copyOf(r.context().tenantRoles()))
                .claim(ClaimNames.IDP, r.idp())
                .claim(ClaimNames.TOKEN_USE, "access")
                .claim(ClaimNames.CLAIMS_VERSION, ClaimNames.CURRENT_VERSION)
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plus(r.ttl())))
                .jwtID(UUID.randomUUID().toString());
        if (r.sessionId() != null) {
            b.claim(ClaimNames.SESSION_ID, r.sessionId());
        }
        if (r.actor() != null) {
            b.claim(ClaimNames.ACTOR, r.actor());
        }
        return b.build();
    }
}
