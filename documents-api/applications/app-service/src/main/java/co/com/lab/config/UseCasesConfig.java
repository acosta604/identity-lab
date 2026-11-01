package co.com.lab.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/** Convencion del scaffold: los casos de uso no llevan anotaciones de Spring; se registran aqui. */
@Configuration
@ComponentScan(basePackages = "co.com.lab.usecase",
        includeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "^.+UseCase$")
        },
        useDefaultFilters = false)
public class UseCasesConfig {
}
