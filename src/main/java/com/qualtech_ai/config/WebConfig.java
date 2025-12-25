package com.qualtech_ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.lang.NonNull;

/**
 * Web MVC Configuration.
 * 
 * NOTE: Do NOT use @EnableWebMvc here - it disables Spring Boot's
 * auto-configuration
 * for static resources which causes issues with SPA routing.
 * 
 * Static resources are served automatically by Spring Boot from
 * classpath:/static/
 * SPA routing is handled by the ErrorController (see SpaController).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // Swagger UI webjars
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}