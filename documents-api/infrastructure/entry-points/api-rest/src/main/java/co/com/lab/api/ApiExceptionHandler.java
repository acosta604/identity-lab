package co.com.lab.api;

import co.com.lab.model.exception.AccessDeniedException;
import co.com.lab.model.exception.DocumentNotFoundException;
import co.com.lab.model.exception.InvalidPrincipalException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, String>> forbidden(AccessDeniedException e) {
        return error(HttpStatus.FORBIDDEN, "access_denied", "OpenFGA: " + e.getMessage());
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(DocumentNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "not_found", e.getMessage());
    }

    @ExceptionHandler(InvalidPrincipalException.class)
    ResponseEntity<Map<String, String>> invalidToken(InvalidPrincipalException e) {
        return error(HttpStatus.UNAUTHORIZED, "invalid_token", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, "invalid_request", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> unavailable(IllegalStateException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "unavailable", e.getMessage());
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String code, String description) {
        return ResponseEntity.status(status).body(Map.of("error", code, "error_description", description));
    }
}
