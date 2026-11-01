package lab.exchange.session;

import lab.commons.LabCookies;
import lab.exchange.config.LabProperties;
import lab.exchange.error.ExchangeException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Sesion propia. Nace en el exchange con el token de Entra y desde ahi vive sin volver a Entra.
 * <p>
 * La cookie lleva "id.secreto". En la base solo se guarda el SHA-256 del secreto. Cada refresh rota el
 * secreto; si alguien presenta el secreto anterior (cookie robada y ya usada) se revoca la sesion.
 */
@Service
public class SessionService {

    private final JdbcClient jdbc;
    private final LabProperties props;
    private final Clock clock;

    public SessionService(JdbcClient jdbc, LabProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.props = props;
        this.clock = clock;
    }

    public record Session(UUID id, String userId, String tenantId, String organizationId, String idp,
                          Instant lastSeenAt, Instant expiresAt, Instant revokedAt,
                          byte[] secretHash, byte[] previousHash) {
    }

    /** Sesion valida mas el nuevo valor de cookie a enviar. */
    public record Issued(Session session, String cookieValue) {
    }

    @Transactional
    public Issued create(String userId, String tenantId, String organizationId, String idp, String userAgent) {
        UUID id = UUID.randomUUID();
        String secret = LabCookies.randomToken();
        Instant now = clock.instant();
        jdbc.sql("""
                        insert into sessions (id, user_id, secret_hash, tenant_id, organization_id, idp,
                                              created_at, last_seen_at, expires_at, user_agent)
                        values (:id, :user, :hash, :tenant, :org, :idp, :now, :now, :exp, :ua)
                        """)
                .param("id", id)
                .param("user", userId)
                .param("hash", sha256(secret))
                .param("tenant", tenantId)
                .param("org", organizationId)
                .param("idp", idp)
                .param("now", Timestamp.from(now))
                .param("exp", Timestamp.from(now.plus(props.sessionAbsolute())))
                .param("ua", userAgent)
                .update();
        return new Issued(find(id).orElseThrow(), format(id, secret));
    }

    /** Valida la cookie sin rotarla (consultar sesion, cambiar de organizacion). */
    @Transactional(noRollbackFor = ExchangeException.class)
    public Session validate(String cookieValue) {
        Parsed parsed = parse(cookieValue);
        Session s = load(parsed.id());
        byte[] hash = sha256(parsed.secret());
        if (!MessageDigest.isEqual(hash, s.secretHash())) {
            throw ExchangeException.invalidSession("Sesion invalida");
        }
        checkLifetime(s);
        touch(s.id());
        return s;
    }

    /**
     * Valida y rota el secreto. Si llega el secreto anterior, revoca la sesion completa.
     * noRollbackFor: la revocacion debe quedar guardada aunque se responda con error.
     */
    @Transactional(noRollbackFor = ExchangeException.class)
    public Issued rotate(String cookieValue) {
        Parsed parsed = parse(cookieValue);
        Session s = load(parsed.id());
        byte[] hash = sha256(parsed.secret());
        if (s.previousHash() != null && MessageDigest.isEqual(hash, s.previousHash())) {
            revoke(s.id());
            throw ExchangeException.invalidSession("Reuso de una sesion ya rotada: sesion revocada");
        }
        if (!MessageDigest.isEqual(hash, s.secretHash())) {
            throw ExchangeException.invalidSession("Sesion invalida");
        }
        checkLifetime(s);

        String newSecret = LabCookies.randomToken();
        int updated = jdbc.sql("""
                        update sessions
                           set previous_hash = secret_hash, secret_hash = :newHash, last_seen_at = :now
                         where id = :id and secret_hash = :oldHash and revoked_at is null
                        """)
                .param("newHash", sha256(newSecret))
                .param("now", Timestamp.from(clock.instant()))
                .param("id", s.id())
                .param("oldHash", hash)
                .update();
        if (updated != 1) {
            throw ExchangeException.invalidSession("La sesion cambio mientras se rotaba");
        }
        return new Issued(load(s.id()), format(s.id(), newSecret));
    }

    @Transactional
    public void changeContext(UUID id, String tenantId, String organizationId) {
        jdbc.sql("update sessions set tenant_id = :tenant, organization_id = :org where id = :id")
                .param("tenant", tenantId)
                .param("org", organizationId)
                .param("id", id)
                .update();
    }

    @Transactional
    public void revoke(UUID id) {
        jdbc.sql("update sessions set revoked_at = :now where id = :id and revoked_at is null")
                .param("now", Timestamp.from(clock.instant()))
                .param("id", id)
                .update();
    }

    /** Para logout: identifica la sesion aunque ya este vencida, sin lanzar error. */
    public Optional<UUID> idFromCookie(String cookieValue) {
        try {
            Parsed parsed = parse(cookieValue);
            return find(parsed.id())
                    .filter(s -> MessageDigest.isEqual(sha256(parsed.secret()), s.secretHash()))
                    .map(Session::id);
        } catch (ExchangeException e) {
            return Optional.empty();
        }
    }

    private void checkLifetime(Session s) {
        Instant now = clock.instant();
        if (s.revokedAt() != null) {
            throw ExchangeException.invalidSession("Sesion revocada");
        }
        if (now.isAfter(s.expiresAt())) {
            revoke(s.id());
            throw ExchangeException.invalidSession("La sesion supero su vida maxima; inicia sesion de nuevo");
        }
        if (now.isAfter(s.lastSeenAt().plus(props.sessionIdle()))) {
            revoke(s.id());
            throw ExchangeException.invalidSession("Sesion cerrada por inactividad; inicia sesion de nuevo");
        }
    }

    private void touch(UUID id) {
        jdbc.sql("update sessions set last_seen_at = :now where id = :id")
                .param("now", Timestamp.from(clock.instant()))
                .param("id", id)
                .update();
    }

    private Session load(UUID id) {
        return find(id).orElseThrow(() -> ExchangeException.invalidSession("Sesion inexistente"));
    }

    private Optional<Session> find(UUID id) {
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

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    record Parsed(UUID id, String secret) {
    }

    static String format(UUID id, String secret) {
        return id + "." + secret;
    }

    static Parsed parse(String cookieValue) {
        if (cookieValue == null) {
            throw ExchangeException.invalidSession("No hay sesion");
        }
        int dot = cookieValue.indexOf('.');
        if (dot <= 0 || dot == cookieValue.length() - 1) {
            throw ExchangeException.invalidSession("Cookie de sesion mal formada");
        }
        try {
            return new Parsed(UUID.fromString(cookieValue.substring(0, dot)), cookieValue.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            throw ExchangeException.invalidSession("Cookie de sesion mal formada");
        }
    }

    static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
