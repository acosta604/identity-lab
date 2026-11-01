package lab.exchange.api;

import lab.exchange.config.LabProperties;
import lab.exchange.error.ExchangeException;
import lab.exchange.signing.KeyManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Llaves publicas y metadatos del emisor propio, para que las APIs validen el JWT sin llamar al servicio. */
@RestController
public class JwksController {

    private final KeyManager keys;
    private final LabProperties props;

    public JwksController(KeyManager keys, LabProperties props) {
        this.keys = keys;
        this.props = props;
    }

    @GetMapping("/token/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return keys.publicJwks();
    }

    @GetMapping("/token/.well-known/openid-configuration")
    public Map<String, Object> discovery() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("issuer", props.issuer());
        m.put("jwks_uri", props.issuer() + "/.well-known/jwks.json");
        m.put("token_endpoint", props.issuer() + "/exchange");
        m.put("grant_types_supported", List.of(TokenExchangeController.GRANT_TYPE));
        m.put("id_token_signing_alg_values_supported", List.of("RS256"));
        return m;
    }

    /** Solo laboratorio: rota la llave de firma. Los tokens viejos siguen validos hasta vencer. */
    @PostMapping("/token/admin/rotate-keys")
    public Map<String, Object> rotate() {
        if (!props.adminEnabled()) {
            throw new ExchangeException(HttpStatus.NOT_FOUND, "not_found", "Endpoint deshabilitado");
        }
        return Map.of("active_kid", keys.rotate());
    }
}
