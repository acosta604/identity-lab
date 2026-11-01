package lab.exchange.identity;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** APIs para las que se puede emitir token, con su vida maxima. */
@Component
public class AudienceRegistry {

    private final JdbcClient jdbc;

    public AudienceRegistry(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Duration> maxTtl(String audience) {
        return jdbc.sql("select max_ttl_seconds from allowed_audiences where audience = :aud")
                .param("aud", audience)
                .query(Integer.class)
                .optional()
                .map(Duration::ofSeconds);
    }
}
