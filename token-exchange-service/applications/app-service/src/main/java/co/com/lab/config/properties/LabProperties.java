package co.com.lab.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuracion del laboratorio (prefijo "lab").
 *
 * @param issuer          "iss" del token propio (URL publica, a traves del proxy)
 * @param accessTokenTtl  vida maxima del JWT propio
 * @param sessionIdle     cierre de la sesion propia por inactividad
 * @param sessionAbsolute vida maxima de la sesion propia
 * @param defaultAudience audiencia si el exchange no la indica
 * @param cookiesSecure   true = cookies Secure con prefijo __Host-
 * @param adminEnabled    habilita POST /token/admin/rotate-keys
 */
@ConfigurationProperties(prefix = "lab")
public record LabProperties(
        String issuer,
        Duration accessTokenTtl,
        Duration sessionIdle,
        Duration sessionAbsolute,
        String defaultAudience,
        boolean cookiesSecure,
        boolean adminEnabled) {
}
