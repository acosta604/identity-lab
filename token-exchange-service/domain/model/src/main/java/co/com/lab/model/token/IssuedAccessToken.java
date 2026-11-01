package co.com.lab.model.token;

import java.time.Duration;

/** JWT propio ya firmado. */
public record IssuedAccessToken(String value, Duration ttl) {
}
