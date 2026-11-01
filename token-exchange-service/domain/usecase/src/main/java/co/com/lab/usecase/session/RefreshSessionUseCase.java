package co.com.lab.usecase.session;

import co.com.lab.model.accesscontext.AccessContext;
import co.com.lab.model.audit.AuditEvent;
import co.com.lab.model.audit.gateways.AuditGateway;
import co.com.lab.model.exception.BusinessException;
import co.com.lab.model.exception.ErrorType;
import co.com.lab.model.session.Session;
import co.com.lab.model.session.SessionPolicy;
import co.com.lab.model.session.SessionToken;
import co.com.lab.model.session.gateways.SessionRepository;
import co.com.lab.model.token.IssuedAccessToken;
import co.com.lab.model.token.TokenPolicy;
import co.com.lab.model.token.gateways.AccessTokenGateway;
import co.com.lab.model.token.gateways.AudiencePolicyGateway;
import co.com.lab.model.user.User;
import co.com.lab.model.user.gateways.UserDirectoryGateway;
import co.com.lab.usecase.token.AccessTokenIssuer;

import java.time.Clock;
import java.time.Instant;

/**
 * Renueva el JWT propio sin volver a Entra. Rota el secreto de la sesion y relee las membresias,
 * asi el token nuevo refleja cambios de equipo o rol. Reusar un secreto ya rotado revoca la sesion.
 */
public class RefreshSessionUseCase {

    private final SessionRepository sessions;
    private final UserDirectoryGateway directory;
    private final AuditGateway audit;
    private final AccessTokenIssuer issuer;
    private final SessionPolicy policy;
    private final Clock clock;

    public RefreshSessionUseCase(SessionRepository sessions, UserDirectoryGateway directory, AuditGateway audit,
                                 AccessTokenGateway tokens, AudiencePolicyGateway audiences,
                                 TokenPolicy tokenPolicy, SessionPolicy policy, Clock clock) {
        this.sessions = sessions;
        this.directory = directory;
        this.audit = audit;
        this.issuer = new AccessTokenIssuer(tokens, audiences, tokenPolicy);
        this.policy = policy;
        this.clock = clock;
    }

    public SessionResult execute(String sessionCookie) {
        Instant now = clock.instant();
        Session session;
        SessionToken rotated;
        try {
            SessionToken presented = SessionToken.parse(sessionCookie);
            session = sessions.findById(presented.sessionId())
                    .orElseThrow(() -> Session.invalid("Sesion inexistente"));
            if (session.isPreviousSecret(presented)) {
                sessions.revoke(session.id(), now);
                throw Session.invalid("Reuso de una sesion ya rotada: sesion revocada");
            }
            if (!session.isCurrentSecret(presented)) {
                throw Session.invalid("Sesion invalida");
            }
            SessionSupport.checkLifetime(sessions, session, now, policy);

            rotated = SessionToken.generate(session.id());
            if (!sessions.rotateSecret(session.id(), presented.secretHash(), rotated.secretHash(), now)) {
                throw Session.invalid("La sesion cambio mientras se rotaba");
            }
        } catch (BusinessException e) {
            audit.record(AuditEvent.of("REFRESH_REJECTED").detail(e.getMessage()));
            throw e;
        }

        User user = SessionSupport.activeUser(directory, sessions, session, now);
        AccessContext context = directory.resolveContext(session.userId(), session.tenantId(), session.organizationId())
                .orElseThrow(() -> {
                    audit.record(AuditEvent.of("NO_MEMBERSHIP").user(session.userId())
                            .context(session.tenantId(), session.organizationId()));
                    return new BusinessException(ErrorType.ACCESS_DENIED,
                            "Ya no eres miembro de la organizacion activa; elige otra con POST /session/context");
                });
        IssuedAccessToken access = issuer.issue(user, context, issuer.defaultAudience(), session.idp(),
                session.id().toString(), null);
        audit.record(AuditEvent.of("REFRESHED").user(user.id())
                .context(context.tenantId(), context.organizationId()).idp(session.idp()));
        return new SessionResult(context, access, rotated);
    }
}
