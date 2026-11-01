package co.com.lab.usecase.session;

import co.com.lab.model.exception.BusinessException;
import co.com.lab.model.exception.ErrorType;
import co.com.lab.model.session.Session;
import co.com.lab.model.session.SessionPolicy;
import co.com.lab.model.session.SessionToken;
import co.com.lab.model.session.gateways.SessionRepository;
import co.com.lab.model.user.User;
import co.com.lab.model.user.gateways.UserDirectoryGateway;

import java.time.Instant;

/** Reglas compartidas por los casos de uso de sesion. No es un bean: no termina en "UseCase". */
final class SessionSupport {

    private SessionSupport() {
    }

    /** Valida la cookie contra la sesion guardada sin rotarla. Revoca si esta vencida o inactiva. */
    static Session validate(SessionRepository sessions, SessionToken token, Instant now, SessionPolicy policy) {
        Session session = sessions.findById(token.sessionId())
                .orElseThrow(() -> Session.invalid("Sesion inexistente"));
        if (!session.isCurrentSecret(token)) {
            throw Session.invalid("Sesion invalida");
        }
        checkLifetime(sessions, session, now, policy);
        return session;
    }

    static void checkLifetime(SessionRepository sessions, Session session, Instant now, SessionPolicy policy) {
        switch (session.lifetimeProblem(now, policy)) {
            case NONE -> {
            }
            case REVOKED -> throw Session.invalid("Sesion revocada");
            case EXPIRED -> {
                sessions.revoke(session.id(), now);
                throw Session.invalid("La sesion supero su vida maxima; inicia sesion de nuevo en Entra");
            }
            case IDLE -> {
                sessions.revoke(session.id(), now);
                throw Session.invalid("Sesion cerrada por inactividad; inicia sesion de nuevo en Entra");
            }
        }
    }

    static User activeUser(UserDirectoryGateway directory, SessionRepository sessions, Session session, Instant now) {
        return directory.findActiveUser(session.userId()).orElseThrow(() -> {
            sessions.revoke(session.id(), now);
            return new BusinessException(ErrorType.INVALID_SESSION, "Usuario inactivo");
        });
    }
}
