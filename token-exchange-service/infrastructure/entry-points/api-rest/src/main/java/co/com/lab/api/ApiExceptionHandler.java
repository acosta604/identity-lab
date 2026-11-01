package co.com.lab.api;

import co.com.lab.api.cookie.LabCookies;
import co.com.lab.model.exception.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Traduce los errores del dominio a HTTP con codigos al estilo OAuth2. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private final LabCookies cookies;

    public ApiExceptionHandler(LabCookies cookies) {
        this.cookies = cookies;
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<Map<String, String>> handle(BusinessException e) {
        record Mapping(HttpStatus status, String code) {
        }
        Mapping m = switch (e.getType()) {
            case INVALID_REQUEST -> new Mapping(HttpStatus.BAD_REQUEST, "invalid_request");
            case INVALID_TARGET -> new Mapping(HttpStatus.BAD_REQUEST, "invalid_target");
            case INVALID_SUBJECT -> new Mapping(HttpStatus.UNAUTHORIZED, "invalid_token");
            case ACCESS_DENIED -> new Mapping(HttpStatus.FORBIDDEN, "access_denied");
            case INVALID_SESSION -> new Mapping(HttpStatus.UNAUTHORIZED, "invalid_session");
            case NOT_FOUND -> new Mapping(HttpStatus.NOT_FOUND, "not_found");
        };
        var response = ResponseEntity.status(m.status());
        if ("invalid_session".equals(m.code())) {
            // Sesion invalida: se borran las cookies para que el cliente vuelva a iniciar sesion en Entra.
            cookies.clearAll().forEach(c -> response.header(HttpHeaders.SET_COOKIE, c.toString()));
        }
        if (m.status() == HttpStatus.UNAUTHORIZED) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"" + m.code() + "\"");
        }
        return response.body(Map.of("error", m.code(), "error_description", e.getMessage()));
    }
}
