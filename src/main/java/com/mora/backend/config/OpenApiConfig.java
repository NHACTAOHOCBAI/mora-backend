package com.mora.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String port;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Value("${springdoc.swagger-ui.path:/swagger-ui.html}")
    private String swaggerPath;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mora API Documentation")
                        .version("1.0.0")
                        .description("Tài liệu kham khảo các RESTful API của hệ thống Mora Backend")
                        .contact(new Contact()
                                .name("Mora Team")
                                .email("contact@mora.com")));
    }

    @Bean
    public ApplicationListener<ApplicationReadyEvent> onApplicationReady() {
        return event -> {
            String path = contextPath + swaggerPath;
            path = path.replace("//", "/");
            log.info("Application is ready! Access Swagger UI at: http://localhost:{}{}", port, path);
        };
    }
}

