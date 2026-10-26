
package com.pulse.api.config;

import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PublicApiOpenApiConfig {

    @Bean
    public OpenApiCustomizer publicApiSecurityCustomizer() {
        return openApi -> {
            Operation register = openApi.getPaths()
                    .get("/api/v1/users/register")
                    .getPost();
            register.setSecurity(java.util.Collections.emptyList());

            Operation login = openApi.getPaths()
                    .get("/api/v1/auth/login")
                    .getPost();
            login.setSecurity(java.util.Collections.emptyList());
        };
    }
}
