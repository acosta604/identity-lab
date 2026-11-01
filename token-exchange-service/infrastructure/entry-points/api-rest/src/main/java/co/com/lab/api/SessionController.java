package co.com.lab.api;

import co.com.lab.api.cookie.LabCookies;
import co.com.lab.model.session.SessionPolicy;
import co.com.lab.usecase.session.ChangeContextUseCase;
import co.com.lab.usecase.session.GetSessionUseCase;
import co.com.lab.usecase.session.LogoutUseCase;
import co.com.lab.usecase.session.RefreshSessionUseCase;
import co.com.lab.usecase.session.SessionResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Todo lo que pasa despues del inicio de sesion, sin volver a Entra. */
@RestController
@RequestMapping("/session")
public class SessionController {

    private final RefreshSessionUseCase refresh;
    private final ChangeContextUseCase changeContext;
    private final GetSessionUseCase getSession;
    private final LogoutUseCase logout;
    private final LabCookies cookies;
    private final SessionPolicy sessionPolicy;

    public SessionController(RefreshSessionUseCase refresh, ChangeContextUseCase changeContext,
                             GetSessionUseCase getSession, LogoutUseCase logout, LabCookies cookies,
                             SessionPolicy sessionPolicy) {
        this.refresh = refresh;
        this.changeContext = changeContext;
        this.getSession = getSession;
        this.logout = logout;
        this.cookies = cookies;
        this.sessionPolicy = sessionPolicy;
    }

    /** Rota la sesion y emite un JWT con claims frescos (relee membresias). */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(HttpServletRequest request) {
        return respond(refresh.execute(sessionCookie(request)));
    }

    public record ContextRequest(String tenant, String organization) {
    }

    /** Cambia la organizacion activa. */
    @PostMapping("/context")
    public ResponseEntity<Map<String, Object>> context(@RequestBody ContextRequest body, HttpServletRequest request) {
        return respond(changeContext.execute(sessionCookie(request), body.tenant(), body.organization()));
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        var view = getSession.execute(sessionCookie(request));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user", Map.of("id", view.user().id(), "name", view.user().name(), "email", view.user().email()));
        body.put("session", Map.of(
                "id", view.session().id().toString(),
                "idp", view.session().idp(),
                "expires_at", view.session().expiresAt().toString()));
        view.context().ifPresent(c -> body.put("context", ContextResponse.of(c)));
        body.put("organizations", view.organizations());
        return body;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        logout.execute(cookies.read(request, cookies.sessionName()).orElse(null));
        var response = ResponseEntity.noContent();
        cookies.clearAll().forEach(c -> response.header(HttpHeaders.SET_COOKIE, c.toString()));
        return response.build();
    }

    private ResponseEntity<Map<String, Object>> respond(SessionResult r) {
        var response = ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.access(r.accessToken().value(), r.accessToken().ttl()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (r.newSessionToken() != null) {
            response.header(HttpHeaders.SET_COOKIE,
                    cookies.session(r.newSessionToken().value(), sessionPolicy.absoluteLife()).toString());
        }
        return response.body(Map.of(
                "expires_in", r.accessToken().ttl().toSeconds(),
                "context", ContextResponse.of(r.context())));
    }

    private String sessionCookie(HttpServletRequest request) {
        return cookies.read(request, cookies.sessionName()).orElse(null);
    }
}
