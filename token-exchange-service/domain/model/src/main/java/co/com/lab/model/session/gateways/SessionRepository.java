package co.com.lab.model.session.gateways;

import co.com.lab.model.session.Session;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {

    void create(NewSession session);

    Optional<Session> findById(UUID id);

    /** Cambia el secreto solo si el vigente sigue siendo oldHash. false = alguien lo cambio antes. */
    boolean rotateSecret(UUID id, byte[] oldHash, byte[] newHash, Instant now);

    void touch(UUID id, Instant now);

    void changeContext(UUID id, String tenantId, String organizationId);

    void revoke(UUID id, Instant now);

    record NewSession(UUID id, String userId, byte[] secretHash, String tenantId, String organizationId,
                      String idp, Instant createdAt, Instant expiresAt, String userAgent) {
    }
}
