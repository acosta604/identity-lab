package co.com.lab.config;

import co.com.lab.config.properties.LabProperties;
import co.com.lab.model.session.SessionPolicy;
import co.com.lab.model.token.TokenPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Traduce la configuracion a objetos del dominio, para que los casos de uso no dependan de Spring. */
@Configuration
public class DomainConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TokenPolicy tokenPolicy(LabProperties props) {
        return new TokenPolicy(props.accessTokenTtl(), props.defaultAudience());
    }

    @Bean
    SessionPolicy sessionPolicy(LabProperties props) {
        return new SessionPolicy(props.sessionIdle(), props.sessionAbsolute());
    }
}
