package co.com.lab.model.session;

import co.com.lab.model.exception.BusinessException;
import co.com.lab.model.exception.ErrorType;

import java.time.Instant;
import java.util.UUID;

/**
 * Sesion propia: nace en el exchange con el token de Entra y vive sin volver a Entra.
 *
 * @param secretHash   hash del secreto vigente de la cookie
 * @param previousHash hash del secreto anterior; si vuelve a llegar, la cookie fue robada y reusada
 */
public record Session(UUID id, String userId, String tenantId, String organizationId, String idp,
                      Instant lastSeenAt, Instant expiresAt, Instant revokedAt,
                      byte[] secretHash, byte[] previousHash) {

    public enum Problem { NONE, REVOKED, EXPIRED, IDLE }

    /** Regla de vigencia: no revocada, dentro de la vida maxima y sin superar la inactividad. */
    public Problem lifetimeProblem(Instant now, SessionPolicy policy) {
        if (revokedAt != null) {
            return Problem.REVOKED;
        }
        if (now.isAfter(expiresAt)) {
            return Problem.EXPIRED;
        }
        if (now.isAfter(lastSeenAt.plus(policy.idleTimeout()))) {
            return Problem.IDLE;
        }
        return Problem.NONE;
    }

    public boolean isCurrentSecret(SessionToken token) {
        return token.matches(secretHash);
    }

    public boolean isPreviousSecret(SessionToken token) {
        return token.matches(previousHash);
    }

    public static BusinessException invalid(String message) {
        return new BusinessException(ErrorType.INVALID_SESSION, message);
    }
}
