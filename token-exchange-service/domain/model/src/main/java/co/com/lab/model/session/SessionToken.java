package co.com.lab.model.session;

import co.com.lab.model.exception.BusinessException;
import co.com.lab.model.exception.ErrorType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Valor de la cookie de sesion: "id.secreto". En la base solo se guarda el SHA-256 del secreto,
 * asi que una fuga de la base no permite suplantar sesiones.
 */
public record SessionToken(UUID sessionId, String secret) {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static SessionToken generate(UUID sessionId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return new SessionToken(sessionId, Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    public static SessionToken parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorType.INVALID_SESSION, "No hay sesion; inicia sesion en Entra y haz el exchange");
        }
        int dot = value.indexOf('.');
        if (dot <= 0 || dot == value.length() - 1) {
            throw new BusinessException(ErrorType.INVALID_SESSION, "Cookie de sesion mal formada");
        }
        try {
            return new SessionToken(UUID.fromString(value.substring(0, dot)), value.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorType.INVALID_SESSION, "Cookie de sesion mal formada");
        }
    }

    public String value() {
        return sessionId + "." + secret;
    }

    public byte[] secretHash() {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean matches(byte[] hash) {
        return hash != null && MessageDigest.isEqual(secretHash(), hash);
    }
}
