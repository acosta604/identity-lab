package lab.exchange.subject;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import lab.exchange.config.LabProperties;
import lab.exchange.error.ExchangeException;
import lab.exchange.signing.TokenSigner;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.text.ParseException;

/**
 * Valida el subject_token del exchange. Dos casos:
 * <ul>
 *   <li>Token de Entra (inicio de sesion): se valida contra trusted_issuers.</li>
 *   <li>Token propio (servicio a servicio): se valida con nuestras llaves.</li>
 * </ul>
 */
@Component
public class SubjectTokenValidator {

    private final TrustedIssuerRegistry registry;
    private final TokenSigner signer;
    private final LabProperties props;

    public SubjectTokenValidator(TrustedIssuerRegistry registry, TokenSigner signer, LabProperties props) {
        this.registry = registry;
        this.signer = signer;
        this.props = props;
    }

    public sealed interface Subject permits External, Internal {
    }

    /** Persona que viene de Entra: issuer + identificador externo (oid). */
    public record External(String issuer, String externalSubject) implements Subject {
    }

    /** Token propio ya emitido: trae el usuario interno y su contexto. */
    public record Internal(JWTClaimsSet claims) implements Subject {
    }

    public Subject validate(String token) {
        String issuer = unverifiedIssuer(token);
        if (props.issuer().equals(issuer)) {
            return new Internal(signer.verifyOwn(token));
        }
        var trusted = registry.find(issuer)
                .orElseThrow(() -> ExchangeException.badSubject("Emisor no confiable: " + issuer));
        Jwt jwt;
        try {
            jwt = registry.decoder(trusted).decode(token);
        } catch (JwtException e) {
            throw ExchangeException.badSubject("Token de Entra rechazado: " + e.getMessage());
        }
        String externalSubject = jwt.getClaimAsString(trusted.subjectClaim());
        if (externalSubject == null || externalSubject.isBlank()) {
            throw ExchangeException.badSubject("El token no trae el claim " + trusted.subjectClaim());
        }
        return new External(trusted.issuer(), externalSubject);
    }

    /** Solo para elegir como validar; la firma se verifica despues. */
    private static String unverifiedIssuer(String token) {
        try {
            String iss = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
            if (iss == null) {
                throw ExchangeException.badSubject("El token no trae iss");
            }
            return iss;
        } catch (ParseException e) {
            throw ExchangeException.badSubject("subject_token mal formado");
        }
    }
}
