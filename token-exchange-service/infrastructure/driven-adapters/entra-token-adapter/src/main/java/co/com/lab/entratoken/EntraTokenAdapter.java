package co.com.lab.entratoken;

import co.com.lab.model.exception.BusinessException;
import co.com.lab.model.exception.ErrorType;
import co.com.lab.model.identity.ExternalIdentity;
import co.com.lab.model.identity.gateways.ExternalTokenGateway;
import com.nimbusds.jwt.JWTParser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.text.ParseException;

/** Valida el token del inicio de sesion (Entra o el mock) y extrae solo la identidad. */
@Component
public class EntraTokenAdapter implements ExternalTokenGateway {

    private final TrustedIssuerRegistry registry;

    public EntraTokenAdapter(TrustedIssuerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ExternalIdentity verify(String token) {
        String issuer = unverifiedIssuer(token);
        var trusted = registry.find(issuer).orElseThrow(() -> invalid("Emisor no confiable: " + issuer));
        Jwt jwt;
        try {
            jwt = registry.decoder(trusted).decode(token);
        } catch (JwtException e) {
            throw invalid("Token de Entra rechazado: " + e.getMessage());
        }
        String subject = jwt.getClaimAsString(trusted.subjectClaim());
        if (subject == null || subject.isBlank()) {
            throw invalid("El token no trae el claim " + trusted.subjectClaim());
        }
        return new ExternalIdentity(trusted.issuer(), subject);
    }

    /** Solo para buscar el emisor en la lista blanca; la firma se verifica despues. */
    private static String unverifiedIssuer(String token) {
        try {
            String iss = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
            if (iss == null) {
                throw invalid("El token no trae iss");
            }
            return iss;
        } catch (ParseException e) {
            throw invalid("subject_token mal formado");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorType.INVALID_SUBJECT, message);
    }
}
