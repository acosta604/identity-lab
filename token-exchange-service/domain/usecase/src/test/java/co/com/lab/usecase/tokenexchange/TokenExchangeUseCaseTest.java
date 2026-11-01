package co.com.lab.usecase.tokenexchange;

import co.com.lab.model.accesscontext.AccessContext;
import co.com.lab.model.accesscontext.OrganizationSummary;
import co.com.lab.model.audit.AuditEvent;
import co.com.lab.model.exception.BusinessException;
import co.com.lab.model.exception.ErrorType;
import co.com.lab.model.identity.ExternalIdentity;
import co.com.lab.model.session.Session;
import co.com.lab.model.session.SessionPolicy;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Prueba el caso de uso con dobles en memoria: sin Spring, sin base y sin JWT reales. */
class TokenExchangeUseCaseTest {

    private static final String ENTRA = "http://localhost:8090/entra";

    private final List<AuditEvent> audit = new ArrayList<>();
    private final List<SessionRepository.NewSession> createdSessions = new ArrayList<>();
    private AccessTokenRequest lastRequest;

    private final AccessContext ventas = new AccessContext("acme", "acme-ventas", "member",
            List.of(new AccessContext.TeamMembership("acme-ventas-norte", "lead")), List.of());

    private final TokenExchangeUseCase useCase = new TokenExchangeUseCase(
            token -> {
                if (token.equals("entra-alice")) {
                    return new ExternalIdentity(ENTRA, "alice");
                }
                if (token.equals("entra-mallory")) {
                    return new ExternalIdentity(ENTRA, "mallory");
                }
                throw new BusinessException(ErrorType.INVALID_SUBJECT, "Emisor no confiable");
            },
            new AccessTokenGateway() {
                @Override
                public IssuedAccessToken issue(AccessTokenRequest request) {
                    lastRequest = request;
                    return new IssuedAccessToken("jwt-propio", request.ttl());
                }

                @Override
                public boolean isOwnToken(String token) {
                    return token.startsWith("propio-");
                }

                @Override
                public AccessTokenClaims verify(String token) {
                    return new AccessTokenClaims("alice", "acme", "acme-ventas", ENTRA, "sid-1", null);
                }
            },
            new UserDirectoryGateway() {
                @Override
                public Optional<String> findLinkedUser(String issuer, String externalSubject) {
                    return ENTRA.equals(issuer) && "alice".equals(externalSubject) ? Optional.of("alice") : Optional.empty();
                }

                @Override
                public Optional<User> findActiveUser(String userId) {
                    return Optional.of(new User(userId, "Alice", "alice@lab.local"));
                }

                @Override
                public Optional<AccessContext> resolveContext(String userId, String tenantId, String organizationId) {
                    return organizationId == null || organizationId.equals("acme-ventas") ? Optional.of(ventas) : Optional.empty();
                }

                @Override
                public List<OrganizationSummary> organizationsOf(String userId) {
                    return List.of();
                }
            },
            new SessionRepository() {
                @Override
                public void create(NewSession session) {
                    createdSessions.add(session);
                }

                @Override
                public Optional<Session> findById(UUID id) {
                    return Optional.empty();
                }

                @Override
                public boolean rotateSecret(UUID id, byte[] oldHash, byte[] newHash, Instant now) {
                    return false;
                }

                @Override
                public void touch(UUID id, Instant now) {
                }

                @Override
                public void changeContext(UUID id, String tenantId, String organizationId) {
                }

                @Override
                public void revoke(UUID id, Instant now) {
                }
            },
            audience -> audience.equals("documents-api") ? Optional.of(Duration.ofMinutes(5))
                    : audience.equals("reports-api") ? Optional.of(Duration.ofMinutes(3)) : Optional.empty(),
            audit::add,
            new TokenPolicy(Duration.ofMinutes(10), "documents-api"),
            new SessionPolicy(Duration.ofMinutes(30), Duration.ofHours(12)),
            Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void elTokenDeEntraSeCambiaPorSesionYJwtPropio() {
        var result = useCase.execute(new TokenExchangeUseCase.Command("entra-alice", null, null, null, null, "ua"));

        var login = assertInstanceOf(TokenExchangeUseCase.LoginResult.class, result);
        assertEquals("alice", login.user().id());
        assertEquals("acme-ventas", login.context().organizationId());
        assertEquals(1, createdSessions.size());
        assertEquals("documents-api", lastRequest.audience());
        assertEquals(Duration.ofMinutes(5), lastRequest.ttl(), "la audiencia baja la vida del token");
        assertEquals(login.sessionToken().sessionId().toString(), lastRequest.sessionId());
        assertNull(lastRequest.actor());
        assertEquals("ISSUED", audit.getLast().result());
    }

    @Test
    void identidadSinVinculoEsAccesoDenegado() {
        var e = assertThrows(BusinessException.class, () ->
                useCase.execute(new TokenExchangeUseCase.Command("entra-mallory", null, null, null, null, null)));
        assertEquals(ErrorType.ACCESS_DENIED, e.getType());
        assertEquals("NOT_LINKED", audit.getLast().result());
    }

    @Test
    void organizacionAjenaEsAccesoDenegado() {
        var e = assertThrows(BusinessException.class, () ->
                useCase.execute(new TokenExchangeUseCase.Command("entra-alice", null, "acme", "globex-main", null, null)));
        assertEquals(ErrorType.ACCESS_DENIED, e.getType());
        assertEquals("NO_MEMBERSHIP", audit.getLast().result());
    }

    @Test
    void emisorNoConfiableSeAudita() {
        var e = assertThrows(BusinessException.class, () ->
                useCase.execute(new TokenExchangeUseCase.Command("otro", null, null, null, null, null)));
        assertEquals(ErrorType.INVALID_SUBJECT, e.getType());
        assertEquals("BAD_SUBJECT", audit.getLast().result());
    }

    @Test
    void audienciaNoPermitidaEsInvalidTarget() {
        var e = assertThrows(BusinessException.class, () ->
                useCase.execute(new TokenExchangeUseCase.Command("entra-alice", "graph", null, null, null, null)));
        assertEquals(ErrorType.INVALID_TARGET, e.getType());
    }

    @Test
    void servicioAServicioEmiteConActorYSinSesion() {
        var result = useCase.execute(new TokenExchangeUseCase.Command("propio-xyz", "reports-api", null, null,
                "documents-api", null));

        assertInstanceOf(TokenExchangeUseCase.DelegatedResult.class, result);
        assertEquals(0, createdSessions.size());
        assertEquals("reports-api", lastRequest.audience());
        assertEquals("service:documents-api", lastRequest.actor().subject());
        assertEquals("sid-1", lastRequest.sessionId());
    }
}
