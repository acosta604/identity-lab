package co.com.lab.model.token.gateways;

import java.time.Duration;
import java.util.Optional;

/** APIs para las que se puede emitir token y su vida maxima. Vacio = audiencia no permitida. */
public interface AudiencePolicyGateway {

    Optional<Duration> maxTtl(String audience);
}
