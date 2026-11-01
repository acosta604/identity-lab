package co.com.lab.model.token.gateways;

import java.util.Map;

/** Llaves de firma del token propio. */
public interface SigningKeyGateway {

    /** JWKS publico (formato RFC 7517) con la llave activa y las anteriores. */
    Map<String, Object> publicJwks();

    /** Genera una llave nueva y la deja activa. Devuelve su kid. */
    String rotate();
}
