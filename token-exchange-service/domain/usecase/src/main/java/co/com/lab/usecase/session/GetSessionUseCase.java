package co.com.lab.usecase.session;

import co.com.lab.model.accesscontext.AccessContext;
import co.com.lab.model.accesscontext.OrganizationSummary;
import co.com.lab.model.session.Session;
import co.com.lab.model.session.SessionPolicy;
import co.com.lab.model.session.SessionToken;
import co.com.lab.model.session.gateways.SessionRepository;
import co.com.lab.model.user.User;
import co.com.lab.model.user.gateways.UserDirectoryGateway;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Usuario, contexto activo y organizaciones disponibles de la sesion actual. */
public class GetSessionUseCase {

    private final SessionRepository sessions;
    private final UserDirectoryGateway directory;
    private final SessionPolicy policy;
    private final Clock clock;

    public GetSessionUseCase(SessionRepository sessions, UserDirectoryGateway directory, SessionPolicy policy,
                             Clock clock) {
        this.sessions = sessions;
        this.directory = directory;
        this.policy = policy;
        this.clock = clock;
    }

    public record View(User user, Session session, Optional<AccessContext> context,
                       List<OrganizationSummary> organizations) {
    }

    public View execute(String sessionCookie) {
        Instant now = clock.instant();
        Session session = SessionSupport.validate(sessions, SessionToken.parse(sessionCookie), now, policy);
        sessions.touch(session.id(), now);
        User user = SessionSupport.activeUser(directory, sessions, session, now);
        return new View(user, session,
                directory.resolveContext(user.id(), session.tenantId(), session.organizationId()),
                directory.organizationsOf(user.id()));
    }
}
