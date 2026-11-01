package co.com.lab.usecase.signingkey;

import co.com.lab.model.token.gateways.SigningKeyGateway;

import java.util.Map;

/** Publica las llaves publicas (JWKS) y rota la llave de firma. */
public class SigningKeyUseCase {

    private final SigningKeyGateway keys;

    public SigningKeyUseCase(SigningKeyGateway keys) {
        this.keys = keys;
    }

    public Map<String, Object> publicKeys() {
        return keys.publicJwks();
    }

    /** Los tokens firmados con la llave anterior siguen validos hasta vencer: su llave sigue en el JWKS. */
    public String rotate() {
        return keys.rotate();
    }
}
