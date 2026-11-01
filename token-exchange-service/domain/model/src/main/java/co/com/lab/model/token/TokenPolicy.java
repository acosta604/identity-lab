package co.com.lab.model.token;

import java.time.Duration;

/**
 * Politica de emision del token propio.
 *
 * @param maxTtl          vida maxima del JWT propio (cada audiencia puede bajarla)
 * @param defaultAudience audiencia cuando no se envia
 */
public record TokenPolicy(Duration maxTtl, String defaultAudience) {

    /** Vida efectiva: la menor entre la politica general y la de la audiencia. */
    public Duration effectiveTtl(Duration audienceMax) {
        return audienceMax.compareTo(maxTtl) < 0 ? audienceMax : maxTtl;
    }
}
