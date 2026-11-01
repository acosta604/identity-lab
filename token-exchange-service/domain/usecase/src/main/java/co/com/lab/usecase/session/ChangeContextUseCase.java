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

/** Cambia la organizacion (o tenant) activa y emite un JWT con esos claims. Sin volver a Entra. */
public class ChangeContextUseCase {

    private final SessionRepository sessions;
    private final UserDirectoryGateway directory;
    private final AuditGateway audit;
    private final AccessTokenIssuer issuer;
    private final SessionPolicy policy;
    private final Clock clock;

    public ChangeContextUseCase(SessionRepository sessions, UserDirectoryGateway directory, AuditGateway audit,
                                AccessTokenGateway tokens, AudiencePolicyGateway audiences,
                                TokenPolicy tokenPolicy, SessionPolicy policy, Clock clock) {
        this.sessions = sessions;
        this.directory = directory;
        this.audit = audit;
        this.issuer = new AccessTokenIssuer(tokens, audiences, tokenPolicy);
        this.policy = policy;
        this.clock = clock;
    }

    public SessionResult execute(String sessionCookie, String tenantId, String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            throw new BusinessException(ErrorType.INVALID_REQUEST, "Falta organization");
        }
        Instant now = clock.instant();
        Session session = SessionSupport.validate(sessions, SessionToken.parse(sessionCookie), now, policy);
        sessions.touch(session.id(), now);
        User user = SessionSupport.activeUser(directory, sessions, session, now);
        String tenant = tenantId == null || tenantId.isBlank() ? null : tenantId;

        AccessContext context = directory.resolveContext(user.id(), tenant, organizationId).orElseThrow(() -> {
            audit.record(AuditEvent.of("NO_MEMBERSHIP").user(user.id()).context(tenantId, organizationId));
            return new BusinessException(ErrorType.ACCESS_DENIED,
                    "No eres miembro activo de esa organizacion o no pertenece al tenant");
        });
        sessions.changeContext(session.id(), context.tenantId(), context.organizationId());
        IssuedAccessToken access = issuer.issue(user, context, issuer.defaultAudience(), session.idp(),
                session.id().toString(), null);
        audit.record(AuditEvent.of("CONTEXT_CHANGED").user(user.id())
                .context(context.tenantId(), context.organizationId()).idp(session.idp()));
        return new SessionResult(context, access, null);
    }
}
