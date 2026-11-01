package co.com.lab.model.exception;

/** Tipos de error del dominio. El entry point los traduce a HTTP y a codigos OAuth2. */
public enum ErrorType {
    /** Peticion mal formada (400 invalid_request). */
    INVALID_REQUEST,
    /** Audiencia no permitida (400 invalid_target). */
    INVALID_TARGET,
    /** Token de entrada invalido o de un emisor no confiable (401 invalid_token). */
    INVALID_SUBJECT,
    /** Identidad valida pero sin vinculo o sin membresia (403 access_denied). */
    ACCESS_DENIED,
    /** Sesion propia inexistente, vencida, revocada o reusada (401 invalid_session). */
    INVALID_SESSION,
    /** Recurso o funcionalidad no disponible (404). */
    NOT_FOUND
}
