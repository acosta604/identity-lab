package lab.exchange.error;

import lab.commons.LabCookies;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ErrorHandler {

    private final LabCookies cookies;

    public ErrorHandler(LabCookies cookies) {
        this.cookies = cookies;
    }

    @ExceptionHandler(ExchangeException.class)
    ResponseEntity<Map<String, String>> handle(ExchangeException e) {
        var builder = ResponseEntity.status(e.status());
        if ("invalid_session".equals(e.error())) {
            // Sesion invalida: se limpian las cookies para que el cliente vuelva a iniciar sesion en Entra.
            cookies.clearAll().forEach(c -> builder.header(HttpHeaders.SET_COOKIE, c.toString()));
        }
        if (e.status() == HttpStatus.UNAUTHORIZED) {
            builder.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"" + e.error() + "\"");
        }
        return builder.body(Map.of("error", e.error(), "error_description", e.getMessage()));
    }
}
