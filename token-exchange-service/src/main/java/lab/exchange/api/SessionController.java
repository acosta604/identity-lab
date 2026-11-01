package lab.exchange.api;

import jakarta.servlet.http.HttpServletRequest;
import lab.commons.LabCookies;
import lab.exchange.audit.AuditLog;
import lab.exchange.audit.AuditLog.Event;
import lab.exchange.claims.OrgContext;
import lab.exchange.claims.UserInfo;
import lab.exchange.config.LabProperties;
import lab.exchange.error.ExchangeException;
import lab.exchange.identity.Directory;
import lab.exchange.session.SessionService;
import lab.exchange.session.SessionService.Session;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Todo lo que pasa despues del inicio de sesion, sin volver a Entra:
 * renovar el JWT, cambiar de organizacion, consultar la sesion y cerrarla.
 */
@RestController
@RequestMapping("/session")
public class SessionController {

    private final SessionService sessions;
    private final Directory directory;
    private final AccessTokens accessTokens;
    private final LabCookies cookies;
    private final LabProperties props;
    private final AuditLog audit;

    public SessionController(SessionService sessions, Directory directory, AccessTokens accessTokens,
                             LabCookies cookies, LabProperties props, AuditLog audit) {
        this.sessions = sessions;
        this.directory = directory;
        this.accessTokens = accessTokens;
        this.cookies = cookies;
        this.props = props;
        this.audit = audit;
    }

    /** Rota la sesion y emite un JWT nuevo releyendo las membresias (claims frescos). */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(HttpServletRequest request) {
        SessionService.Issued rotated;
        try {
            rotated = sessions.rotate(sessionCookie(request));
        } catch (ExchangeException e) {
            audit.record(Event.of("REFRESH_REJECTED").detail(e.getMessage()));
            throw e;
        }
        Session s = rotated.session();
        UserInfo user = activeUser(s);
        OrgContext context = directory.context(s.userId(), s.tenantId(), s.organizationId())
                .orElseThrow(() -> {
                    audit.record(Event.of("NO_MEMBERSHIP").user(s.userId()).context(s.tenantId(), s.organizationId()));
                    return ExchangeException.accessDenied(
                            "Ya no eres miembro de la organizacion activa; elige otra con POST /session/context");
                });
        var access = accessTokens.issue(user, context, props.defaultAudience(), s.idp(), s.id().toString(), null);
        audit.record(Event.of("REFRESHED").user(s.userId()).context(context.tenantId(), context.orgId()).idp(s.idp()));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.session(rotated.cookieValue(), props.sessionAbsolute()).toString())
                .header(HttpHeaders.SET_COOKIE, cookies.access(access.token(), access.ttl()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("expires_in", access.ttl().toSeconds(), "context", Responses.context(context)));
    }

    public record ContextRequest(String tenant, String organization) {
    }

    /** Cambia la organizacion (o tenant) activa y emite un JWT con esos claims. */
    @PostMapping("/context")
    public ResponseEntity<Map<String, Object>> changeContext(@RequestBody ContextRequest body, HttpServletRequest request) {
        if (body == null || body.organization() == null || body.organization().isBlank()) {
            throw ExchangeException.invalidRequest("Falta organization");
        }
        Session s = sessions.validate(sessionCookie(request));
        UserInfo user = activeUser(s);
        OrgContext context = directory.context(s.userId(), blankToNull(body.tenant()), body.organization())
                .orElseThrow(() -> {
                    audit.record(Event.of("NO_MEMBERSHIP").user(s.userId()).context(body.tenant(), body.organization()));
                    return ExchangeException.accessDenied("No eres miembro activo de esa organizacion o no pertenece al tenant");
                });
        sessions.changeContext(s.id(), context.tenantId(), context.orgId());
        var access = accessTokens.issue(user, context, props.defaultAudience(), s.idp(), s.id().toString(), null);
        audit.record(Event.of("CONTEXT_CHANGED").user(s.userId()).context(context.tenantId(), context.orgId()).idp(s.idp()));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.access(access.token(), access.ttl()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("expires_in", access.ttl().toSeconds(), "context", Responses.context(context)));
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        Session s = sessions.validate(sessionCookie(request));
        UserInfo user = activeUser(s);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user", Map.of("id", user.id(), "name", user.name(), "email", user.email()));
        body.put("session", Map.of(
                "id", s.id().toString(),
                "idp", s.idp(),
                "expires_at", s.expiresAt().toString()));
        directory.context(s.userId(), s.tenantId(), s.organizationId())
                .ifPresent(c -> body.put("context", Responses.context(c)));
        body.put("organizations", directory.organizations(s.userId()));
        return body;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        LabCookies.read(request, cookies.sessionName())
                .flatMap(sessions::idFromCookie)
                .ifPresent(id -> {
                    sessions.revoke(id);
                    audit.record(Event.of("LOGOUT").detail(id.toString()));
                });
        var response = ResponseEntity.noContent();
        cookies.clearAll().forEach(c -> response.header(HttpHeaders.SET_COOKIE, c.toString()));
        return response.build();
    }

    private String sessionCookie(HttpServletRequest request) {
        return LabCookies.read(request, cookies.sessionName())
                .orElseThrow(() -> ExchangeException.invalidSession("No hay sesion; inicia sesion en Entra y haz el exchange"));
    }

    private UserInfo activeUser(Session s) {
        return directory.user(s.userId()).orElseThrow(() -> {
            sessions.revoke(s.id());
            return ExchangeException.invalidSession("Usuario inactivo");
        });
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
