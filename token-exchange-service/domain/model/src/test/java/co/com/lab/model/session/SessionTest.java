package co.com.lab.model.session;

import co.com.lab.model.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTest {

    private final SessionPolicy policy = new SessionPolicy(Duration.ofMinutes(30), Duration.ofHours(12));
    private final Instant now = Instant.parse("2026-11-01T12:00:00Z");

    @Test
    void elTokenDeSesionSeFormateaYParsea() {
        SessionToken token = SessionToken.generate(UUID.randomUUID());
        SessionToken parsed = SessionToken.parse(token.value());
        assertEquals(token.sessionId(), parsed.sessionId());
        assertArrayEquals(token.secretHash(), parsed.secretHash());
    }

    @Test
    void rechazaCookiesMalFormadas() {
        assertThrows(BusinessException.class, () -> SessionToken.parse("sin-punto"));
        assertThrows(BusinessException.class, () -> SessionToken.parse("no-uuid.secreto"));
        assertThrows(BusinessException.class, () -> SessionToken.parse(null));
    }

    @Test
    void reconoceElSecretoVigenteYElAnterior() {
        SessionToken anterior = SessionToken.generate(UUID.randomUUID());
        SessionToken vigente = SessionToken.generate(anterior.sessionId());
        Session s = session(now, now.plus(Duration.ofHours(1)), null, vigente.secretHash(), anterior.secretHash());

        assertTrue(s.isCurrentSecret(vigente));
        assertFalse(s.isCurrentSecret(anterior));
        assertTrue(s.isPreviousSecret(anterior));
    }

    @Test
    void detectaInactividadVencimientoYRevocacion() {
        byte[] hash = SessionToken.generate(UUID.randomUUID()).secretHash();
        assertEquals(Session.Problem.NONE,
                session(now.minus(Duration.ofMinutes(10)), now.plusSeconds(60), null, hash, null).lifetimeProblem(now, policy));
        assertEquals(Session.Problem.IDLE,
                session(now.minus(Duration.ofMinutes(31)), now.plusSeconds(60), null, hash, null).lifetimeProblem(now, policy));
        assertEquals(Session.Problem.EXPIRED,
                session(now, now.minusSeconds(1), null, hash, null).lifetimeProblem(now, policy));
        assertEquals(Session.Problem.REVOKED,
                session(now, now.plusSeconds(60), now, hash, null).lifetimeProblem(now, policy));
    }

    private Session session(Instant lastSeen, Instant expires, Instant revoked, byte[] hash, byte[] previous) {
        return new Session(UUID.randomUUID(), "alice", "acme", "acme-ventas", "idp", lastSeen, expires, revoked,
                hash, previous);
    }
}
