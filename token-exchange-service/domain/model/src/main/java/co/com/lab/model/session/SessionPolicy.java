package co.com.lab.model.session;

import java.time.Duration;

/**
 * @param idleTimeout  cierre por inactividad
 * @param absoluteLife vida maxima desde el inicio de sesion en Entra
 */
public record SessionPolicy(Duration idleTimeout, Duration absoluteLife) {
}
