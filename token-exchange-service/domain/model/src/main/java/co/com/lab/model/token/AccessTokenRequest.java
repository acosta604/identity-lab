package co.com.lab.model.token;

import co.com.lab.model.accesscontext.AccessContext;
import co.com.lab.model.user.User;

import java.time.Duration;

/**
 * Lo necesario para emitir el JWT propio.
 *
 * @param idp       emisor que autentico a la persona (Entra / mock)
 * @param sessionId sesion propia (claim "sid"); puede ser null
 * @param actor     claim "act"; null salvo en servicio a servicio
 */
public record AccessTokenRequest(User user, AccessContext context, String audience, String idp,
                                 String sessionId, Duration ttl, Actor actor) {
}
