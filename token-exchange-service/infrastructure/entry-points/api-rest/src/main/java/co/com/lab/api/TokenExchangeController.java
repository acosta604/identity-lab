package co.com.lab.api;

import co.com.lab.api.cookie.LabCookies;
import co.com.lab.model.exception.BusinessException;
import co.com.lab.model.exception.ErrorType;
import co.com.lab.model.session.SessionPolicy;
import co.com.lab.usecase.tokenexchange.TokenExchangeUseCase;
import co.com.lab.usecase.tokenexchange.TokenExchangeUseCase.DelegatedResult;
import co.com.lab.usecase.tokenexchange.TokenExchangeUseCase.LoginResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * POST /token/exchange al estilo RFC 8693 (application/x-www-form-urlencoded).
 * Con un token de Entra: deja la sesion y el JWT propio en cookies. Con un token propio: devuelve
 * un token para otra audiencia en el cuerpo (servicio a servicio).
 */
@RestController
public class TokenExchangeController {

    static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange";
    static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";
    static final String JWT_TYPE = "urn:ietf:params:oauth:token-type:jwt";

    private final TokenExchangeUseCase exchange;
    private final LabCookies cookies;
    private final SessionPolicy sessionPolicy;

    public TokenExchangeController(TokenExchangeUseCase exchange, LabCookies cookies, SessionPolicy sessionPolicy) {
        this.exchange = exchange;
        this.cookies = cookies;
        this.sessionPolicy = sessionPolicy;
    }

    @PostMapping(path = "/token/exchange", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> exchange(@RequestParam MultiValueMap<String, String> form,
                                                        HttpServletRequest request) {
        if (!GRANT_TYPE.equals(form.getFirst("grant_type"))) {
            throw new BusinessException(ErrorType.INVALID_REQUEST, "grant_type debe ser " + GRANT_TYPE);
        }
        String subjectTokenType = form.getFirst("subject_token_type");
        if (subjectTokenType != null && !ACCESS_TOKEN_TYPE.equals(subjectTokenType) && !JWT_TYPE.equals(subjectTokenType)) {
            throw new BusinessException(ErrorType.INVALID_REQUEST, "subject_token_type no soportado");
        }

        var result = exchange.execute(new TokenExchangeUseCase.Command(
                form.getFirst("subject_token"),
                form.getFirst("audience"),
                form.getFirst("tenant"),
                form.getFirst("organization"),
                form.getFirst("actor"),
                request.getHeader(HttpHeaders.USER_AGENT)));

        return switch (result) {
            case LoginResult login -> loginResponse(login, "true".equals(form.getFirst("return_token")));
            case DelegatedResult delegated -> delegatedResponse(delegated);
        };
    }

    private ResponseEntity<Map<String, Object>> loginResponse(LoginResult r, boolean returnToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("issued_token_type", ACCESS_TOKEN_TYPE);
        body.put("token_type", "cookie");
        body.put("expires_in", r.accessToken().ttl().toSeconds());
        body.put("user", Map.of("id", r.user().id(), "name", r.user().name()));
        body.put("context", ContextResponse.of(r.context()));
        if (returnToken) {
            // Solo laboratorio (Bruno/curl). Un front no lee el token: vive en la cookie HttpOnly.
            body.put("access_token", r.accessToken().value());
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookies.session(r.sessionToken().value(), sessionPolicy.absoluteLife()).toString())
                .header(HttpHeaders.SET_COOKIE,
                        cookies.access(r.accessToken().value(), r.accessToken().ttl()).toString())
                .header(HttpHeaders.SET_COOKIE, cookies.newXsrf(sessionPolicy.absoluteLife()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    private ResponseEntity<Map<String, Object>> delegatedResponse(DelegatedResult r) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", r.accessToken().value());
        body.put("issued_token_type", ACCESS_TOKEN_TYPE);
        body.put("token_type", "Bearer");
        body.put("expires_in", r.accessToken().ttl().toSeconds());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }
}
