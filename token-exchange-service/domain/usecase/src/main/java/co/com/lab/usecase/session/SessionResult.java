package co.com.lab.usecase.session;

import co.com.lab.model.accesscontext.AccessContext;
import co.com.lab.model.session.SessionToken;
import co.com.lab.model.token.IssuedAccessToken;

/**
 * Resultado de refresh o cambio de contexto.
 *
 * @param newSessionToken nuevo valor de cookie de sesion si se roto; null si no cambio
 */
public record SessionResult(AccessContext context, IssuedAccessToken accessToken, SessionToken newSessionToken) {
}
