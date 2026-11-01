package lab.exchange.error;

import org.springframework.http.HttpStatus;

/** Error con codigo al estilo OAuth2 (RFC 6749 / RFC 8693). */
public class ExchangeException extends RuntimeException {

    private final HttpStatus status;
    private final String error;

    public ExchangeException(HttpStatus status, String error, String description) {
        super(description);
        this.status = status;
        this.error = error;
    }

    public HttpStatus status() {
        return status;
    }

    public String error() {
        return error;
    }

    public static ExchangeException invalidRequest(String description) {
        return new ExchangeException(HttpStatus.BAD_REQUEST, "invalid_request", description);
    }

    public static ExchangeException invalidTarget(String description) {
        return new ExchangeException(HttpStatus.BAD_REQUEST, "invalid_target", description);
    }

    public static ExchangeException badSubject(String description) {
        return new ExchangeException(HttpStatus.UNAUTHORIZED, "invalid_token", description);
    }

    public static ExchangeException accessDenied(String description) {
        return new ExchangeException(HttpStatus.FORBIDDEN, "access_denied", description);
    }

    public static ExchangeException invalidSession(String description) {
        return new ExchangeException(HttpStatus.UNAUTHORIZED, "invalid_session", description);
    }
}
