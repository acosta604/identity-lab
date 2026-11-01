package co.com.lab.model.token.gateways;

import co.com.lab.model.token.AccessTokenClaims;
import co.com.lab.model.token.AccessTokenRequest;
import co.com.lab.model.token.IssuedAccessToken;

/** Emision y validacion del JWT propio (implementado por internal-jwt-adapter). */
public interface AccessTokenGateway {

    IssuedAccessToken issue(AccessTokenRequest request);

    /** true si el token dice venir de este emisor (sin verificar firma: solo para elegir el camino). */
    boolean isOwnToken(String token);

    /** @throws co.com.lab.model.exception.BusinessException INVALID_SUBJECT si no es valido */
    AccessTokenClaims verify(String token);
}
