package co.com.lab.api.cookie;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Cookies del laboratorio. Con secure=true usan el prefijo __Host- (obliga a Secure, Path=/ y sin Domain).
 * Los navegadores aceptan Secure en http://localhost, asi que funciona en local.
 */
@Component
public class LabCookies {

    public static final String XSRF = "XSRF-TOKEN";
    public static final String XSRF_HEADER = "X-XSRF-TOKEN";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final boolean secure;

    public LabCookies(@Value("${lab.cookies-secure:true}") boolean secure) {
        this.secure = secure;
    }

    public String sessionName() {
        return secure ? "__Host-sid" : "sid";
    }

    public String accessName() {
        return secure ? "__Host-at" : "at";
    }

    /** Sesion propia: HttpOnly, Strict. */
    public ResponseCookie session(String value, Duration maxAge) {
        return build(sessionName(), value, true, "Strict", maxAge);
    }

    /** JWT propio: HttpOnly, Lax. */
    public ResponseCookie access(String value, Duration maxAge) {
        return build(accessName(), value, true, "Lax", maxAge);
    }

    /** Token CSRF: legible por JavaScript para copiarlo en X-XSRF-TOKEN. */
    public ResponseCookie newXsrf(Duration maxAge) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return build(XSRF, Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), false, "Strict", maxAge);
    }

    public List<ResponseCookie> clearAll() {
        return List.of(
                build(sessionName(), "", true, "Strict", Duration.ZERO),
                build(accessName(), "", true, "Lax", Duration.ZERO),
                build(XSRF, "", false, "Strict", Duration.ZERO));
    }

    public Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return Optional.of(c.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie build(String name, String value, boolean httpOnly, String sameSite, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
