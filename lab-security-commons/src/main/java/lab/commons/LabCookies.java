package lab.commons;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/** Fabrica y lectura de las cookies del laboratorio. */
public final class LabCookies {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final boolean secure;

    public LabCookies(boolean secure) {
        this.secure = secure;
    }

    public boolean secure() {
        return secure;
    }

    public String sessionName() {
        return CookieNames.session(secure);
    }

    public String accessName() {
        return CookieNames.access(secure);
    }

    /** Sesion: Strict, solo la usa el propio servicio de exchange. */
    public ResponseCookie session(String value, Duration maxAge) {
        return build(sessionName(), value, true, "Strict", maxAge);
    }

    /** Access token: Lax para que llegue tambien al navegar desde otro sitio hacia la app. */
    public ResponseCookie access(String value, Duration maxAge) {
        return build(accessName(), value, true, "Lax", maxAge);
    }

    /** Token CSRF: legible por JavaScript para copiarlo en el header X-XSRF-TOKEN. */
    public ResponseCookie xsrf(String value, Duration maxAge) {
        return build(CookieNames.XSRF, value, false, "Strict", maxAge);
    }

    public ResponseCookie newXsrf(Duration maxAge) {
        return xsrf(randomToken(), maxAge);
    }

    /** Cookies vacias con Max-Age=0 para borrar la sesion en el navegador. */
    public List<ResponseCookie> clearAll() {
        return List.of(
                build(sessionName(), "", true, "Strict", Duration.ZERO),
                build(accessName(), "", true, "Lax", Duration.ZERO),
                build(CookieNames.XSRF, "", false, "Strict", Duration.ZERO));
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

    public static Optional<String> read(HttpServletRequest request, String name) {
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

    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
