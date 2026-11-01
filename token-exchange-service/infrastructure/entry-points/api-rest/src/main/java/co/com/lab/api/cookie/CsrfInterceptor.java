package co.com.lab.api.cookie;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * CSRF con "double submit cookie": si la peticion trae cookies de credenciales y cambia estado,
 * el header X-XSRF-TOKEN debe ser igual a la cookie XSRF-TOKEN. Sin cookies (Bearer, servicio a
 * servicio, o el primer exchange) no aplica: no hay credenciales que el navegador adjunte solo.
 */
@Component
public class CsrfInterceptor implements HandlerInterceptor {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final LabCookies cookies;

    public CsrfInterceptor(LabCookies cookies) {
        this.cookies = cookies;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }
        boolean hasCredentials = cookies.read(request, cookies.sessionName()).isPresent()
                || cookies.read(request, cookies.accessName()).isPresent();
        if (!hasCredentials) {
            return true;
        }
        String cookie = cookies.read(request, LabCookies.XSRF).orElse(null);
        String header = request.getHeader(LabCookies.XSRF_HEADER);
        if (cookie != null && header != null && MessageDigest.isEqual(
                cookie.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":\"csrf\",\"error_description\":\"Falta o no coincide X-XSRF-TOKEN con la cookie XSRF-TOKEN\"}");
        return false;
    }
}
