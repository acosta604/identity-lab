package co.com.lab.usecase.tokenexchange;

import co.com.lab.model.accesscontext.AccessContext;
import co.com.lab.model.audit.AuditEvent;
import co.com.lab.model.audit.gateways.AuditGateway;
import co.com.lab.model.exception.BusinessException;
import co.com.lab.model.exception.ErrorType;
import co.com.lab.model.identity.ExternalIdentity;
import co.com.lab.model.identity.gateways.ExternalTokenGateway;
import co.com.lab.model.session.SessionPolicy;
import co.com.lab.model.session.SessionToken;
import co.com.lab.model.session.gateways.SessionRepository;
import co.com.lab.model.token.AccessTokenClaims;
import co.com.lab.model.token.Actor;
import co.com.lab.model.token.IssuedAccessToken;
import co.com.lab.model.token.TokenPolicy;
import co.com.lab.model.token.gateways.AccessTokenGateway;
import co.com.lab.model.token.gateways.AudiencePolicyGateway;
import co.com.lab.model.user.User;
import co.com.lab.model.user.gateways.UserDirectoryGateway;
import co.com.lab.usecase.token.AccessTokenIssuer;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * JWT exchange (RFC 8693). Dos caminos segun quien emitio el subject_token:
 * <ul>
 *   <li><b>Entra</b> (inicio de sesion): valida el token, lo descarta, vincula la identidad con el usuario
 *       interno, resuelve su contexto, crea la sesion propia y emite el JWT propio.</li>
 *   <li><b>Token propio</b> (servicio a servicio): relee la membresia y emite un token para otra
 *       audiencia con el claim "act". No crea sesion.</li>
 * </ul>
 */
public class TokenExchangeUseCase {

    private final ExternalTokenGateway externalTokens;
    private final AccessTokenGateway ownTokens;
    private final UserDirectoryGateway directory;
    private final SessionRepository sessions;
    private final AuditGateway audit;
    private final AccessTokenIssuer issuer;
    private final SessionPolicy sessionPolicy;
    private final Clock clock;

    public TokenExchangeUseCase(ExternalTokenGateway externalTokens, AccessTokenGateway ownTokens,
                                UserDirectoryGateway directory, SessionRepository sessions,
                                AudiencePolicyGateway audiences, AuditGateway audit,
                                TokenPolicy tokenPolicy, SessionPolicy sessionPolicy, Clock clock) {
        this.externalTokens = externalTokens;
        this.ownTokens = ownTokens;
        this.directory = directory;
        this.sessions = sessions;
        this.audit = audit;
        this.issuer = new AccessTokenIssuer(ownTokens, audiences, tokenPolicy);
        this.sessionPolicy = sessionPolicy;
        this.clock = clock;
    }

    /**
     * @param actor  nombre del servicio que pide el token (solo servicio a servicio)
     * @param userAgent para registrar en la sesion
     */
    public record Command(String subjectToken, String audience, String tenantId, String organizationId,
                         String actor, String userAgent) {
    }

    public sealed interface Result permits LoginResult, DelegatedResult {
    }

    /** Inicio de sesion: hay que entregar sesion + access token en cookies. */
    public record LoginResult(User user, AccessContext context, SessionToken sessionToken,
                              IssuedAccessToken accessToken) implements Result {
    }

    /** Servicio a servicio: solo un access token en el cuerpo. */
    public record DelegatedResult(IssuedAccessToken accessToken) implements Result {
    }

    public Result execute(Command command) {
        if (command.subjectToken() == null || command.subjectToken().isBlank()) {
            throw new BusinessException(ErrorType.INVALID_REQUEST, "Falta subject_token");
        }
        String audience = issuer.resolveAudience(command.audience());

        if (ownTokens.isOwnToken(command.subjectToken())) {
            AccessTokenClaims claims = verify(() -> ownTokens.verify(command.subjectToken()), audience);
            return delegate(claims, audience, command.actor());
        }
        ExternalIdentity identity = verify(() -> externalTokens.verify(command.subjectToken()), audience);
        return login(identity, audience, command);
    }

    private LoginResult login(ExternalIdentity identity, String audience, Command command) {
        AuditEvent event = AuditEvent.of("ISSUED").idp(identity.issuer()).audience(audience);

        String userId = directory.findLinkedUser(identity.issuer(), identity.subject()).orElseThrow(() -> {
            audit.record(event.withResult("NOT_LINKED").detail(identity.subject()));
            return new BusinessException(ErrorType.ACCESS_DENIED,
                    "La identidad de Entra no esta vinculada a un usuario interno");
        });
        User user = directory.findActiveUser(userId)
                .orElseThrow(() -> new BusinessException(ErrorType.ACCESS_DENIED, "Usuario inactivo"));
        AccessContext context = directory.resolveContext(userId, blankToNull(command.tenantId()),
                blankToNull(command.organizationId())).orElseThrow(() -> {
            audit.record(event.withResult("NO_MEMBERSHIP").user(userId)
                    .context(command.tenantId(), command.organizationId()));
            return new BusinessException(ErrorType.ACCESS_DENIED,
                    "No eres miembro activo de esa organizacion o no pertenece al tenant");
        });

        Instant now = clock.instant();
        SessionToken sessionToken = SessionToken.generate(UUID.randomUUID());
        sessions.create(new SessionRepository.NewSession(sessionToken.sessionId(), userId, sessionToken.secretHash(),
                context.tenantId(), context.organizationId(), identity.issuer(), now,
                now.plus(sessionPolicy.absoluteLife()), command.userAgent()));

        IssuedAccessToken access = issuer.issue(user, context, audience, identity.issuer(),
                sessionToken.sessionId().toString(), null);
        audit.record(event.user(userId).context(context.tenantId(), context.organizationId()));
        return new LoginResult(user, context, sessionToken, access);
    }

    private DelegatedResult delegate(AccessTokenClaims claims, String audience, String actorName) {
        User user = directory.findActiveUser(claims.userId())
                .orElseThrow(() -> new BusinessException(ErrorType.ACCESS_DENIED, "Usuario inactivo"));
        // Se relee la membresia: si cambio desde el token original, el nuevo ya lo refleja.
        AccessContext context = directory.resolveContext(claims.userId(), claims.tenantId(), claims.organizationId())
                .orElseThrow(() -> new BusinessException(ErrorType.ACCESS_DENIED, "Ya no eres miembro de esa organizacion"));
        Actor actor = new Actor("service:" + (actorName == null || actorName.isBlank() ? "desconocido" : actorName),
                claims.actor());
        IssuedAccessToken access = issuer.issue(user, context, audience, claims.idp(), claims.sessionId(), actor);
        audit.record(AuditEvent.of("ISSUED_S2S").user(user.id()).context(context.tenantId(), context.organizationId())
                .idp(claims.idp()).audience(audience).detail(actor.subject()));
        return new DelegatedResult(access);
    }

    private <T> T verify(java.util.function.Supplier<T> verification, String audience) {
        try {
            return verification.get();
        } catch (BusinessException e) {
            audit.record(AuditEvent.of("BAD_SUBJECT").audience(audience).detail(e.getMessage()));
            throw e;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
