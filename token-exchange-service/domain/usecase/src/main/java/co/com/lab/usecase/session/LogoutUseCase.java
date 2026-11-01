package co.com.lab.usecase.session;

import co.com.lab.model.audit.AuditEvent;
import co.com.lab.model.audit.gateways.AuditGateway;
import co.com.lab.model.exception.BusinessException;
import co.com.lab.model.session.SessionToken;
import co.com.lab.model.session.gateways.SessionRepository;

import java.time.Clock;

/** Revoca la sesion propia. No falla si ya no existe: el entry point borra las cookies igual. */
public class LogoutUseCase {

    private final SessionRepository sessions;
    private final AuditGateway audit;
    private final Clock clock;

    public LogoutUseCase(SessionRepository sessions, AuditGateway audit, Clock clock) {
        this.sessions = sessions;
        this.audit = audit;
        this.clock = clock;
    }

    public void execute(String sessionCookie) {
        SessionToken token;
        try {
            token = SessionToken.parse(sessionCookie);
        } catch (BusinessException e) {
            return;
        }
        sessions.findById(token.sessionId())
                .filter(s -> s.isCurrentSecret(token))
                .ifPresent(s -> {
                    sessions.revoke(s.id(), clock.instant());
                    audit.record(AuditEvent.of("LOGOUT").user(s.userId()).detail(s.id().toString()));
                });
    }
}
