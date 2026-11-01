package lab.exchange.signing;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Llaves RS256 del token propio. Se guardan en la base solo por ser un laboratorio;
 * en produccion la llave privada vive en un KMS / Key Vault y nunca sale de ahi.
 * El JWKS publica todas las llaves (activa y anteriores) para que los tokens viejos sigan validando
 * mientras vencen.
 */
@Component
public class KeyManager {

    private static final Logger log = LoggerFactory.getLogger(KeyManager.class);

    private final JdbcClient jdbc;
    private volatile Map<String, RSAKey> keys = Map.of();
    private volatile String activeKid;

    public KeyManager(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        reload();
        if (activeKid == null) {
            rotate();
        }
    }

    public RSAKey activeKey() {
        return keys.get(activeKid);
    }

    public Optional<RSAKey> find(String kid) {
        return Optional.ofNullable(kid).map(keys::get);
    }

    /** JWKS con solo las partes publicas. */
    public Map<String, Object> publicJwks() {
        List<JWK> publicKeys = new ArrayList<>();
        keys.values().forEach(k -> publicKeys.add(k.toPublicJWK()));
        return new JWKSet(publicKeys).toJSONObject(true);
    }

    /** Genera una llave nueva, la deja activa y conserva las anteriores en el JWKS. */
    @Transactional
    public synchronized String rotate() {
        try {
            RSAKey key = new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .issueTime(new Date())
                    .generate();
            jdbc.sql("update signing_keys set active = false where active").update();
            jdbc.sql("insert into signing_keys (kid, private_jwk, public_jwk, active) values (:kid, :priv, :pub, true)")
                    .param("kid", key.getKeyID())
                    .param("priv", key.toJSONString())
                    .param("pub", key.toPublicJWK().toJSONString())
                    .update();
            log.info("Nueva llave de firma activa: kid={}", key.getKeyID());
            reload();
            return key.getKeyID();
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo generar la llave RSA", e);
        }
    }

    private synchronized void reload() {
        Map<String, RSAKey> loaded = new LinkedHashMap<>();
        String[] active = {null};
        jdbc.sql("select kid, private_jwk, active from signing_keys order by created_at desc")
                .query(rs -> {
                    try {
                        RSAKey key = RSAKey.parse(rs.getString("private_jwk"));
                        loaded.put(rs.getString("kid"), key);
                        if (rs.getBoolean("active")) {
                            active[0] = rs.getString("kid");
                        }
                    } catch (ParseException e) {
                        throw new IllegalStateException("Llave corrupta en signing_keys", e);
                    }
                });
        this.keys = Map.copyOf(loaded);
        this.activeKid = active[0];
    }
}
