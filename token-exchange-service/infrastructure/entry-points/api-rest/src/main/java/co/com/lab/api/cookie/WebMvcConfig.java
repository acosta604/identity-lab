package co.com.lab.api.cookie;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CsrfInterceptor csrf;

    public WebMvcConfig(CsrfInterceptor csrf) {
        this.csrf = csrf;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(csrf).addPathPatterns("/token/**", "/session/**");
    }
}
