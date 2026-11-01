package co.com.lab.model.identity.gateways;

import co.com.lab.model.identity.ExternalIdentity;

/** Valida el token de inicio de sesion (Entra) contra los emisores de confianza. */
public interface ExternalTokenGateway {

    /** @throws co.com.lab.model.exception.BusinessException INVALID_SUBJECT si no es valido */
    ExternalIdentity verify(String token);
}
