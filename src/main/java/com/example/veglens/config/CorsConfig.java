package com.example.veglens.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            // no trailing slashes!
            .allowedOrigins(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "https://veglens-frontend.vercel.app" // your ngrok domain
            )
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false)
            .maxAge(3600);
    }
}
