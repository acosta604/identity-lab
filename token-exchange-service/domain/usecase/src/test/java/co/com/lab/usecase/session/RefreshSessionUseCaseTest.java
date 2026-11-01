package co.com.lab.usecase.session;

import co.com.lab.model.accesscontext.AccessContext;
import co.com.lab.model.accesscontext.OrganizationSummary;
import co.com.lab.model.exception.BusinessException;
import co.com.lab.model.exception.ErrorType;
import co.com.lab.model.session.Session;
import co.com.lab.model.session.SessionPolicy;
import co.com.lab.model.session.SessionToken;
import co.com.lab.model.session.gateways.SessionRepository;
import co.com.lab.model.token.AccessTokenClaims;
import co.com.lab.model.token.AccessTokenRequest;
import co.com.lab.model.token.IssuedAccessToken;
import co.com.lab.model.token.TokenPolicy;
import co.com.lab.model.token.gateways.AccessTokenGateway;
import co.com.lab.model.user.User;
import co.com.lab.model.user.gateways.UserDirectoryGateway;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshSessionUseCaseTest {

    private final Instant now = Instant.parse("2026-11-01T12:00:00Z");
    private final Map<UUID, Session> store = new HashMap<>();
    private List<AccessContext.TeamMembership> aliceTeams =
            List.of(new AccessContext.TeamMembership("acme-ventas-norte", "lead"));
    private AccessTokenRequest lastRequest;

    private final SessionRepository sessions = new SessionRepository() {
        @Override
        public void create(NewSession s) {
            store.put(s.id(), new Session(s.id(), s.userId(), s.tenantId(), s.organizationId(), s.idp(), s.createdAt(),
                    s.expiresAt(), null, s.secretHash(), null));
        }

        @Override
        public Optional<Session> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public boolean rotateSecret(UUID id, byte[] oldHash, byte[] newHash, Instant at) {
            Session s = store.get(id);
            if (s == null || s.revokedAt() != null || !java.util.Arrays.equals(s.secretHash(), oldHash)) {
                return false;
            }
            store.put(id, new Session(id, s.userId(), s.tenantId(), s.organizationId(), s.idp(), at, s.expiresAt(),
                    null, newHash, oldHash));
            return true;
        }

        @Override
        public void touch(UUID id, Instant at) {
        }

        @Override
        public void changeContext(UUID id, String tenantId, String organizationId) {
        }

        @Override
        public void revoke(UUID id, Instant at) {
            Session s = store.get(id);
            store.put(id, new Session(id, s.userId(), s.tenantId(), s.organizationId(), s.idp(), s.lastSeenAt(),
                    s.expiresAt(), at, s.secretHash(), s.previousHash()));
        }
    };

    private final RefreshSessionUseCase useCase = new RefreshSessionUseCase(
            sessions,
            new UserDirectoryGateway() {
                @Override
                public Optional<String> findLinkedUser(String issuer, String externalSubject) {
                    return Optional.empty();
                }

                @Override
                public Optional<User> findActiveUser(String userId) {
                    return Optional.of(new User(userId, "Alice", "alice@lab.local"));
                }

                @Override
                public Optional<AccessContext> resolveContext(String userId, String tenantId, String organizationId) {
                    return Optional.of(new AccessContext("acme", "acme-ventas", "member", aliceTeams, List.of()));
                }

                @Override
                public List<OrganizationSummary> organizationsOf(String userId) {
                    return List.of();
                }
            },
            event -> {
            },
            new AccessTokenGateway() {
                @Override
                public IssuedAccessToken issue(AccessTokenRequest request) {
                    lastRequest = request;
                    return new IssuedAccessToken("jwt", request.ttl());
                }

                @Override
                public boolean isOwnToken(String token) {
                    return false;
                }

                @Override
                public AccessTokenClaims verify(String token) {
                    throw new UnsupportedOperationException();
                }
            },
            audience -> Optional.of(Duration.ofMinutes(10)),
            new TokenPolicy(Duration.ofMinutes(10), "documents-api"),
            new SessionPolicy(Duration.ofMinutes(30), Duration.ofHours(12)),
            Clock.fixed(now, ZoneOffset.UTC));

    private SessionToken newSession() {
        SessionToken token = SessionToken.generate(UUID.randomUUID());
        sessions.create(new SessionRepository.NewSession(token.sessionId(), "alice", token.secretHash(), "acme",
                "acme-ventas", "entra", now, now.plus(Duration.ofHours(12)), null));
        return token;
    }

    @Test
    void rotaLaSesionYReleeLasMembresias() {
        SessionToken original = newSession();
        SessionResult first = useCase.execute(original.value());
        assertEquals(1, lastRequest.context().teams().size());

        aliceTeams = List.of(); // sale del equipo norte en la base
        SessionResult second = useCase.execute(first.newSessionToken().value());

        assertTrue(lastRequest.context().teams().isEmpty(), "el token nuevo ya no trae el equipo");
        assertEquals(original.sessionId(), second.newSessionToken().sessionId());
    }

    @Test
    void reusarUnSecretoRotadoRevocaLaSesion() {
        SessionToken original = newSession();
        SessionResult rotated = useCase.execute(original.value());

        var reuse = assertThrows(BusinessException.class, () -> useCase.execute(original.value()));
        assertEquals(ErrorType.INVALID_SESSION, reuse.getType());
        // Ni siquiera el secreto vigente sirve: la sesion completa quedo revocada.
        assertThrows(BusinessException.class, () -> useCase.execute(rotated.newSessionToken().value()));
    }

    @Test
    void sinCookieEsSesionInvalida() {
        var e = assertThrows(BusinessException.class, () -> useCase.execute(null));
        assertEquals(ErrorType.INVALID_SESSION, e.getType());
    }
}
