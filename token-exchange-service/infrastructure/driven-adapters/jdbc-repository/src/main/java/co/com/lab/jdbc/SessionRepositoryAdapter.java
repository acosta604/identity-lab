package co.com.lab.jdbc;

import co.com.lab.model.session.Session;
import co.com.lab.model.session.gateways.SessionRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SessionRepositoryAdapter implements SessionRepository {

    private final JdbcClient jdbc;

    public SessionRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void create(NewSession s) {
        jdbc.sql("""
                        insert into sessions (id, user_id, secret_hash, tenant_id, organization_id, idp,
                                              created_at, last_seen_at, expires_at, user_agent)
                        values (:id, :user, :hash, :tenant, :org, :idp, :now, :now, :exp, :ua)
                        """)
                .param("id", s.id())
                .param("user", s.userId())
                .param("hash", s.secretHash())
                .param("tenant", s.tenantId())
                .param("org", s.organizationId())
                .param("idp", s.idp())
                .param("now", Timestamp.from(s.createdAt()))
                .param("exp", Timestamp.from(s.expiresAt()))
                .param("ua", s.userAgent())
                .update();
    }

    @Override
    public Optional<Session> findById(UUID id) {
        return jdbc.sql("""
                        select id, user_id, tenant_id, organization_id, idp, last_seen_at, expires_at, revoked_at,
                               secret_hash, previous_hash
                          from sessions where id = :id
                        """)
                .param("id", id)
                .query((rs, i) -> new Session(
                        rs.getObject("id", UUID.class),
                        rs.getString("user_id"),
                        rs.getString("tenant_id"),
                        rs.getString("organization_id"),
                        rs.getString("idp"),
                        instant(rs.getTimestamp("last_seen_at")),
                        instant(rs.getTimestamp("expires_at")),
                        instant(rs.getTimestamp("revoked_at")),
                        rs.getBytes("secret_hash"),
                        rs.getBytes("previous_hash")))
                .optional();
    }

    @Override
    public boolean rotateSecret(UUID id, byte[] oldHash, byte[] newHash, Instant now) {
        return jdbc.sql("""
                        update sessions
                           set previous_hash = secret_hash, secret_hash = :newHash, last_seen_at = :now
                         where id = :id and secret_hash = :oldHash and revoked_at is null
                        """)
                .param("newHash", newHash)
                .param("now", Timestamp.from(now))
                .param("id", id)
                .param("oldHash", oldHash)
                .update() == 1;
    }

    @Override
    public void touch(UUID id, Instant now) {
        jdbc.sql("update sessions set last_seen_at = :now where id = :id")
                .param("now", Timestamp.from(now))
                .param("id", id)
                .update();
    }

    @Override
    public void changeContext(UUID id, String tenantId, String organizationId) {
        jdbc.sql("update sessions set tenant_id = :tenant, organization_id = :org where id = :id")
                .param("tenant", tenantId)
                .param("org", organizationId)
                .param("id", id)
                .update();
    }

    @Override
    public void revoke(UUID id, Instant now) {
        jdbc.sql("update sessions set revoked_at = :now where id = :id and revoked_at is null")
                .param("now", Timestamp.from(now))
                .param("id", id)
                .update();
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
