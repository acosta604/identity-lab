package co.com.lab.internaljwt;

import co.com.lab.model.accesscontext.AccessContext;
import co.com.lab.model.exception.BusinessException;
import co.com.lab.model.exception.ErrorType;
import co.com.lab.model.token.AccessTokenClaims;
import co.com.lab.model.token.AccessTokenRequest;
import co.com.lab.model.token.Actor;
import co.com.lab.model.token.IssuedAccessToken;
import co.com.lab.model.token.gateways.AccessTokenGateway;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Arma y firma el JWT propio con los claims de usuario, organizacion, equipos y rol.
 * Los claims describen contexto, nunca permisos sobre recursos: eso lo decide OpenFGA.
 */
@Component
public class InternalJwtAdapter implements AccessTokenGateway {

    private final SigningKeyAdapter keys;
    private final String issuer;
    private final Clock clock;

    public InternalJwtAdapter(SigningKeyAdapter keys, @Value("${lab.issuer}") String issuer, Clock clock) {
        this.keys = keys;
        this.issuer = issuer;
        this.clock = clock;
    }

    @Override
    public IssuedAccessToken issue(AccessTokenRequest r) {
        RSAKey key = keys.activeKey();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(key.getKeyID())
                .type(JOSEObjectType.JWT)
                .build();
        SignedJWT jwt = new SignedJWT(header, claims(r));
        try {
            jwt.sign(new RSASSASigner(key));
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo firmar el token", e);
        }
        return new IssuedAccessToken(jwt.serialize(), r.ttl());
    }

    JWTClaimsSet claims(AccessTokenRequest r) {
        Instant now = clock.instant();
        AccessContext c = r.context();

        Map<String, Object> usr = new LinkedHashMap<>();
        usr.put("name", r.user().name());
        usr.put("email", r.user().email());
        Map<String, Object> org = new LinkedHashMap<>();
        org.put("id", c.organizationId());
        org.put("role", c.organizationRole());
        List<Map<String, Object>> teams = c.teams().stream()
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.teamId());
                    m.put("role", t.role());
                    return m;
                })
                .toList();

        JWTClaimsSet.Builder b = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(r.user().id())
                .audience(r.audience())
                .claim(ClaimNames.USER, usr)
                .claim(ClaimNames.TENANT, c.tenantId())
                .claim(ClaimNames.ORGANIZATION, org)
                .claim(ClaimNames.TEAMS, teams)
                .claim(ClaimNames.ROLES, c.tenantRoles())
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
            b.claim(ClaimNames.ACTOR, actorToMap(r.actor()));
        }
        return b.build();
    }

    @Override
    public boolean isOwnToken(String token) {
        try {
            return issuer.equals(JWTParser.parse(token).getJWTClaimsSet().getIssuer());
        } catch (ParseException e) {
            return false;
        }
    }

    @Override
    public AccessTokenClaims verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            RSAKey key = keys.find(jwt.getHeader().getKeyID()).orElseThrow(() -> invalid("kid desconocido"));
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(new RSASSAVerifier(key.toRSAPublicKey()))) {
                throw invalid("Firma invalida");
            }
            JWTClaimsSet c = jwt.getJWTClaimsSet();
            if (!issuer.equals(c.getIssuer())) {
                throw invalid("Emisor inesperado");
            }
            if (c.getExpirationTime() == null || c.getExpirationTime().toInstant().isBefore(clock.instant())) {
                throw invalid("Token vencido");
            }
            if (!"access".equals(c.getStringClaim(ClaimNames.TOKEN_USE))) {
                throw invalid("token_use debe ser access");
            }
            Map<String, Object> org = c.getJSONObjectClaim(ClaimNames.ORGANIZATION);
            return new AccessTokenClaims(
                    c.getSubject(),
                    c.getStringClaim(ClaimNames.TENANT),
                    org == null ? null : (String) org.get("id"),
                    c.getStringClaim(ClaimNames.IDP),
                    c.getStringClaim(ClaimNames.SESSION_ID),
                    actorFromMap(c.getJSONObjectClaim(ClaimNames.ACTOR)));
        } catch (ParseException | JOSEException e) {
            throw invalid("Token mal formado");
        }
    }

    private static Map<String, Object> actorToMap(Actor actor) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sub", actor.subject());
        if (actor.previous() != null) {
            m.put("act", actorToMap(actor.previous()));
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Actor actorFromMap(Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        return new Actor((String) m.get("sub"), actorFromMap((Map<String, Object>) m.get("act")));
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorType.INVALID_SUBJECT, message);
    }
}
