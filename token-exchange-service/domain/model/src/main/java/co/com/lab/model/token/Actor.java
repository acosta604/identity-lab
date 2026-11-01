package co.com.lab.model.token;

/**
 * Claim "act" de RFC 8693: quien actua en nombre de la persona en un exchange servicio a servicio.
 * Se encadena si hay varios saltos.
 */
public record Actor(String subject, Actor previous) {
}
