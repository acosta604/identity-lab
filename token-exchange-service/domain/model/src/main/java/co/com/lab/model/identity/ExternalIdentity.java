package co.com.lab.model.identity;

/**
 * Persona autenticada por Entra (o por el mock): emisor + identificador inmutable (oid).
 * Es todo lo que el dominio necesita del token de Entra; el token se descarta despues del exchange.
 */
public record ExternalIdentity(String issuer, String subject) {
}
