package lab.commons;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * CSRF con patron "double submit cookie".
 * <p>
 * Solo aplica cuando la peticion trae alguna cookie de credenciales (sesion o access token), porque
 * es entonces cuando el navegador las adjunta solo y un sitio malicioso podria aprovecharlo.
 * Una peticion con Authorization: Bearer y sin cookies (servicio a servicio) no lo necesita.
 */
public class DoubleSubmitCsrfFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final Set<String> credentialCookies;

    public DoubleSubmitCsrfFilter(Set<String> credentialCookies) {
        this.credentialCookies = credentialCookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod()) || !hasCredentialCookie(request)) {
            chain.doFilter(request, response);
            return;
        }
        String cookie = LabCookies.read(request, CookieNames.XSRF).orElse(null);
        String header = request.getHeader(CookieNames.XSRF_HEADER);
        if (cookie == null || header == null || !constantTimeEquals(cookie, header)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"csrf\",\"error_description\":\"Falta o no coincide el header X-XSRF-TOKEN con la cookie XSRF-TOKEN\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean hasCredentialCookie(HttpServletRequest request) {
        return credentialCookies.stream().anyMatch(name -> LabCookies.read(request, name).isPresent());
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
