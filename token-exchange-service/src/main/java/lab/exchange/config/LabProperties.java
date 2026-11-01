package lab.exchange.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuracion del servicio de exchange (prefijo "lab" en application.yml).
 *
 * @param issuer          "iss" del token propio
 * @param accessTokenTtl  vida del JWT propio
 * @param sessionIdle     cierre por inactividad de la sesion propia
 * @param sessionAbsolute vida maxima de la sesion propia
 * @param cookiesSecure   true = cookies Secure con prefijo __Host-
 * @param defaultAudience audiencia cuando no se envia el parametro "audience"
 * @param adminEnabled    habilita endpoints de laboratorio (rotar llaves)
 */
@ConfigurationProperties(prefix = "lab")
public record LabProperties(
        String issuer,
        Duration accessTokenTtl,
        Duration sessionIdle,
        Duration sessionAbsolute,
        boolean cookiesSecure,
        String defaultAudience,
        boolean adminEnabled) {
}
