package lab.exchange.api;

import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.http.HttpServletRequest;
import lab.commons.LabCookies;
import lab.exchange.audit.AuditLog;
import lab.exchange.audit.AuditLog.Event;
import lab.exchange.claims.ClaimNames;
import lab.exchange.claims.OrgContext;
import lab.exchange.claims.UserInfo;
import lab.exchange.config.LabProperties;
import lab.exchange.error.ExchangeException;
import lab.exchange.identity.Directory;
import lab.exchange.session.SessionService;
import lab.exchange.subject.SubjectTokenValidator;
import lab.exchange.subject.SubjectTokenValidator.External;
import lab.exchange.subject.SubjectTokenValidator.Internal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT exchange al estilo RFC 8693.
 * <ul>
 *   <li><b>subject_token de Entra</b> (inicio de sesion): valida el token, lo descarta, crea la sesion
 *       propia y deja el JWT propio en cookies. Es el unico momento en que participa Entra.</li>
 *   <li><b>subject_token propio</b> (servicio a servicio): emite un token para otra audiencia con el
 *       claim "act". No crea sesion ni cookies; devuelve el token en el cuerpo.</li>
 * </ul>
 */
@RestController
public class TokenExchangeController {

    static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange";
    static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";
    static final String JWT_TYPE = "urn:ietf:params:oauth:token-type:jwt";

    private final SubjectTokenValidator subjects;
    private final Directory directory;
    private final SessionService sessions;
    private final AccessTokens accessTokens;
    private final LabCookies cookies;
    private final LabProperties props;
    private final AuditLog audit;

    public TokenExchangeController(SubjectTokenValidator subjects, Directory directory, SessionService sessions,
                                   AccessTokens accessTokens, LabCookies cookies, LabProperties props,
                                   AuditLog audit) {
        this.subjects = subjects;
        this.directory = directory;
        this.sessions = sessions;
        this.accessTokens = accessTokens;
        this.cookies = cookies;
        this.props = props;
        this.audit = audit;
    }

    @PostMapping(path = "/token/exchange", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> exchange(@RequestParam MultiValueMap<String, String> form,
                                                        HttpServletRequest request) {
        if (!GRANT_TYPE.equals(form.getFirst("grant_type"))) {
            throw ExchangeException.invalidRequest("grant_type debe ser " + GRANT_TYPE);
        }
        String subjectToken = form.getFirst("subject_token");
        if (subjectToken == null || subjectToken.isBlank()) {
            throw ExchangeException.invalidRequest("Falta subject_token");
        }
        String subjectTokenType = form.getFirst("subject_token_type");
        if (subjectTokenType != null && !ACCESS_TOKEN_TYPE.equals(subjectTokenType) && !JWT_TYPE.equals(subjectTokenType)) {
            throw ExchangeException.invalidRequest("subject_token_type no soportado");
        }
        String audience = valueOr(form.getFirst("audience"), props.defaultAudience());
        accessTokens.ttlFor(audience); // falla rapido con invalid_target si la audiencia no esta permitida

        SubjectTokenValidator.Subject subject;
        try {
            subject = subjects.validate(subjectToken);
        } catch (ExchangeException e) {
            audit.record(Event.of("BAD_SUBJECT").audience(audience).detail(e.getMessage()));
            throw e;
        }
        return switch (subject) {
            case External ext -> login(ext, form, audience, request);
            case Internal internal -> serviceToService(internal.claims(), form, audience);
        };
    }

    /** Inicio de sesion: token de Entra -> sesion propia + JWT propio en cookies. */
    private ResponseEntity<Map<String, Object>> login(External ext, MultiValueMap<String, String> form,
                                                      String audience, HttpServletRequest request) {
        Event event = Event.of("ISSUED").idp(ext.issuer()).audience(audience);

        String userId = directory.linkedUser(ext.issuer(), ext.externalSubject()).orElseThrow(() -> {
            audit.record(event.detail("NOT_LINKED " + ext.externalSubject()).withResult("NOT_LINKED"));
            return ExchangeException.accessDenied("La identidad de Entra no esta vinculada a un usuario interno");
        });
        UserInfo user = directory.user(userId)
                .orElseThrow(() -> ExchangeException.accessDenied("Usuario inactivo"));

        String tenant = blankToNull(form.getFirst("tenant"));
        String organization = blankToNull(form.getFirst("organization"));
        OrgContext context = directory.context(userId, tenant, organization).orElseThrow(() -> {
            audit.record(event.user(userId).context(tenant, organization).withResult("NO_MEMBERSHIP"));
            return ExchangeException.accessDenied("No eres miembro activo de esa organizacion o no pertenece al tenant");
        });

        var session = sessions.create(userId, context.tenantId(), context.orgId(), ext.issuer(),
                request.getHeader(HttpHeaders.USER_AGENT));
        var access = accessTokens.issue(user, context, audience, ext.issuer(),
                session.session().id().toString(), null);
        audit.record(event.user(userId).context(context.tenantId(), context.orgId()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("issued_token_type", ACCESS_TOKEN_TYPE);
        body.put("token_type", "cookie");
        body.put("expires_in", access.ttl().toSeconds());
        body.put("user", Map.of("id", user.id(), "name", user.name()));
        body.put("context", Responses.context(context));
        if ("true".equals(form.getFirst("return_token"))) {
            // Solo para el laboratorio (Bruno/curl). Un front no deberia leer el token: vive en la cookie HttpOnly.
            body.put("access_token", access.token());
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.session(session.cookieValue(), props.sessionAbsolute()).toString())
                .header(HttpHeaders.SET_COOKIE, cookies.access(access.token(), access.ttl()).toString())
                .header(HttpHeaders.SET_COOKIE, cookies.newXsrf(props.sessionAbsolute()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    /** Servicio a servicio: token propio -> token propio para otra API, con "act". Sin Entra. */
    private ResponseEntity<Map<String, Object>> serviceToService(JWTClaimsSet claims, MultiValueMap<String, String> form,
                                                                 String audience) {
        try {
            String userId = claims.getSubject();
            String tenant = claims.getStringClaim(ClaimNames.TENANT);
            Map<String, Object> org = claims.getJSONObjectClaim(ClaimNames.ORGANIZATION);
            String orgId = org == null ? null : (String) org.get("id");
            String idp = claims.getStringClaim(ClaimNames.IDP);

            UserInfo user = directory.user(userId)
                    .orElseThrow(() -> ExchangeException.accessDenied("Usuario inactivo"));
            // Se relee la membresia: si cambio desde que se emitio el token original, el nuevo ya lo refleja.
            OrgContext context = directory.context(userId, tenant, orgId)
                    .orElseThrow(() -> ExchangeException.accessDenied("Ya no eres miembro de esa organizacion"));

            Map<String, Object> actor = new LinkedHashMap<>();
            actor.put("sub", "service:" + valueOr(form.getFirst("actor"), "desconocido"));
            Map<String, Object> previousActor = claims.getJSONObjectClaim(ClaimNames.ACTOR);
            if (previousActor != null) {
                actor.put("act", previousActor);
            }
            var access = accessTokens.issue(user, context, audience, idp,
                    claims.getStringClaim(ClaimNames.SESSION_ID), actor);
            audit.record(Event.of("ISSUED_S2S").user(userId).context(context.tenantId(), context.orgId())
                    .idp(idp).audience(audience).detail(String.valueOf(actor.get("sub"))));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("access_token", access.token());
            body.put("issued_token_type", ACCESS_TOKEN_TYPE);
            body.put("token_type", "Bearer");
            body.put("expires_in", access.ttl().toSeconds());
            return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
        } catch (ParseException e) {
            throw ExchangeException.badSubject("Claims del token propio mal formados");
        }
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
