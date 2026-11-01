package co.com.lab.entratoken;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Emisores de confianza (tabla trusted_issuers). Hoy el mock de Entra; para Entra real se agrega una
 * fila con su issuer, jwks_uri, audience (el App ID URI del exchange), subject_claim='oid' y expected_tid.
 */
@Component
public class TrustedIssuerRegistry {

    private final JdbcClient jdbc;
    private final Map<TrustedIssuer, JwtDecoder> decoders = new ConcurrentHashMap<>();

    public TrustedIssuerRegistry(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record TrustedIssuer(String issuer, String jwksUri, String audience, String subjectClaim,
                                String expectedTid) {
    }

    public Optional<TrustedIssuer> find(String issuer) {
        return jdbc.sql("""
                        select issuer, jwks_uri, audience, subject_claim, expected_tid
                          from trusted_issuers
                         where issuer = :iss and enabled
                        """)
                .param("iss", issuer)
                .query((rs, i) -> new TrustedIssuer(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5)))
                .optional();
    }

    public JwtDecoder decoder(TrustedIssuer ti) {
        return decoders.computeIfAbsent(ti, TrustedIssuerRegistry::buildDecoder);
    }

    private static JwtDecoder buildDecoder(TrustedIssuer ti) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(ti.jwksUri()).build();
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(ti.issuer()));
        // El token debe haber sido emitido para el servicio de exchange, no para Graph ni otra API.
        validators.add(new JwtClaimValidator<Collection<String>>("aud",
                aud -> aud != null && aud.contains(ti.audience())));
        if (ti.expectedTid() != null) {
            // Solo personas del tenant de Azure de la empresa.
            validators.add(new JwtClaimValidator<String>("tid", ti.expectedTid()::equals));
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }
}
