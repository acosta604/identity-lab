package co.com.lab.api;

import co.com.lab.model.exception.BusinessException;
import co.com.lab.model.exception.ErrorType;
import co.com.lab.usecase.signingkey.SigningKeyUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Metadatos del emisor propio: las APIs validan el JWT con estas llaves, sin llamar al servicio. */
@RestController
public class JwksController {

    private final SigningKeyUseCase keys;
    private final String issuer;
    private final boolean adminEnabled;

    public JwksController(SigningKeyUseCase keys, @Value("${lab.issuer}") String issuer,
                          @Value("${lab.admin-enabled:false}") boolean adminEnabled) {
        this.keys = keys;
        this.issuer = issuer;
        this.adminEnabled = adminEnabled;
    }

    @GetMapping("/token/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return keys.publicKeys();
    }

    @GetMapping("/token/.well-known/openid-configuration")
    public Map<String, Object> discovery() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("issuer", issuer);
        m.put("jwks_uri", issuer + "/.well-known/jwks.json");
        m.put("token_endpoint", issuer + "/exchange");
        m.put("grant_types_supported", List.of(TokenExchangeController.GRANT_TYPE));
        m.put("id_token_signing_alg_values_supported", List.of("RS256"));
        return m;
    }

    /** Solo laboratorio (lab.admin-enabled=true): rota la llave de firma. */
    @PostMapping("/token/admin/rotate-keys")
    public Map<String, Object> rotate() {
        if (!adminEnabled) {
            throw new BusinessException(ErrorType.NOT_FOUND, "Endpoint deshabilitado");
        }
        return Map.of("active_kid", keys.rotate());
    }
}
