package lab.exchange.signing;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lab.exchange.claims.ClaimNames;
import lab.exchange.config.LabProperties;
import lab.exchange.error.ExchangeException;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.time.Clock;
import java.util.Date;

/** Firma el JWT propio y verifica los que el mismo servicio emitio (exchange servicio a servicio). */
@Component
public class TokenSigner {

    private final KeyManager keys;
    private final LabProperties props;
    private final Clock clock;

    public TokenSigner(KeyManager keys, LabProperties props, Clock clock) {
        this.keys = keys;
        this.props = props;
        this.clock = clock;
    }

    public String sign(JWTClaimsSet claims) {
        RSAKey key = keys.activeKey();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(key.getKeyID())
                .type(JOSEObjectType.JWT)
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new RSASSASigner(key));
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo firmar el token", e);
        }
        return jwt.serialize();
    }

    /** Valida un token propio: firma, emisor, vencimiento y que sea un access token. */
    public JWTClaimsSet verifyOwn(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            RSAKey key = keys.find(jwt.getHeader().getKeyID())
                    .orElseThrow(() -> ExchangeException.badSubject("kid desconocido"));
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(new RSASSAVerifier(key.toRSAPublicKey()))) {
                throw ExchangeException.badSubject("Firma invalida");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!props.issuer().equals(claims.getIssuer())) {
                throw ExchangeException.badSubject("Emisor inesperado");
            }
            Date exp = claims.getExpirationTime();
            if (exp == null || exp.toInstant().isBefore(clock.instant())) {
                throw ExchangeException.badSubject("Token vencido");
            }
            if (!"access".equals(claims.getStringClaim(ClaimNames.TOKEN_USE))) {
                throw ExchangeException.badSubject("token_use debe ser access");
            }
            return claims;
        } catch (ParseException | JOSEException e) {
            throw ExchangeException.badSubject("Token mal formado");
        }
    }
}
