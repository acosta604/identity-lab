package lab.exchange.config;

import lab.commons.DoubleSubmitCsrfFilter;
import lab.commons.LabCookies;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Clock;
import java.util.Set;

@Configuration
public class WebConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    LabCookies labCookies(LabProperties props) {
        return new LabCookies(props.cookiesSecure());
    }

    @Bean
    FilterRegistrationBean<DoubleSubmitCsrfFilter> csrfFilter(LabCookies cookies) {
        var filter = new DoubleSubmitCsrfFilter(Set.of(cookies.sessionName(), cookies.accessName()));
        var registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/session/*", "/token/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
