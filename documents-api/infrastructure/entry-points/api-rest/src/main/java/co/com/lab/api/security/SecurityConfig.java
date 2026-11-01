package co.com.lab.api.security;

import co.com.lab.api.cookie.LabCookies;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, BearerTokenResolver resolver) throws Exception {
        http
                // CSRF lo maneja CsrfInterceptor (double submit), igual que en el servicio de exchange.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .bearerTokenResolver(resolver)
                        .jwt(Customizer.withDefaults()));
        return http.build();
    }

    /** Solo confia en el emisor propio: un token de Entra se rechaza aunque este bien firmado. */
    @Bean
    JwtDecoder jwtDecoder(@Value("${lab.token-jwks-uri}") String jwksUri,
                          @Value("${lab.token-issuer}") String issuer,
                          @Value("${lab.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new OwnTokenValidator(audience)));
        return decoder;
    }

    /** Authorization: Bearer (servicio a servicio) o, si no viene, la cookie del JWT propio. */
    @Bean
    BearerTokenResolver bearerTokenResolver(LabCookies cookies) {
        DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();
        return request -> {
            String token = header.resolve(request);
            return token != null ? token : cookies.read(request, cookies.accessName()).orElse(null);
        };
    }
}
