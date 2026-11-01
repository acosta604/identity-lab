package co.com.lab.jdbc;

import co.com.lab.model.token.gateways.AudiencePolicyGateway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
public class AudiencePolicyAdapter implements AudiencePolicyGateway {

    private final JdbcClient jdbc;

    public AudiencePolicyAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Duration> maxTtl(String audience) {
        return jdbc.sql("select max_ttl_seconds from allowed_audiences where audience = :aud")
                .param("aud", audience)
                .query(Integer.class)
                .optional()
                .map(Duration::ofSeconds);
    }
}
