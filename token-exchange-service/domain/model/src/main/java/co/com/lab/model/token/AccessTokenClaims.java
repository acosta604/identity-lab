package co.com.lab.model.token;

/** Lo que el dominio necesita de un token propio ya validado (exchange servicio a servicio). */
public record AccessTokenClaims(String userId, String tenantId, String organizationId, String idp,
                                String sessionId, Actor actor) {
}
