package com.miniurl.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration with JWT authentication support.
 *
 * This configuration:
 * - Sets up Swagger UI at /swagger-ui.html
 * - Configures JWT Bearer token authentication
 * - Allows users to authenticate via the Authorize button in Swagger UI
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI miniUrlOpenAPI() {
        // Define security scheme for JWT Bearer authentication
        SecurityScheme jwtSecurityScheme = new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .in(SecurityScheme.In.HEADER)
            .name("Authorization")
            .description("Enter JWT Bearer token obtained from /auth/login endpoint");

        return new OpenAPI()
            .info(new Info()
                .title("MyURL API Documentation")
                .description("""
                    ## MyURL - URL Shortener Service API

                    This API provides endpoints for:
                    - **Authentication**: Login, signup, password reset, email verification
                    - **URL Management**: Create, read, update, delete shortened URLs
                    - **User Profile**: Manage user settings and profile
                    - **Admin**: Administrative endpoints for system management

                    ### Authentication Flow
                    1. Click the **Authorize** button in Swagger UI
                    2. Use the login endpoint with your credentials to obtain a JWT token
                    3. The token will be automatically included in subsequent requests

                    ### Security
                    All API endpoints (except authentication and health endpoints) require JWT Bearer token authentication.

                    > **Security Note**: Never share your credentials or JWT tokens. Always use HTTPS in production.
                    """)
                .version("1.0.0")
                .contact(new Contact()
                    .name("MyURL Support")
                    .email("support@miniurl.com"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:8080")
                    .description("Local Development Server"),
                new Server()
                    .url("https://api.miniurl.com")
                    .description("Production Server"),
                new Server()
                    .url("${app.base-url}")
                    .description("Configured Base URL")))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", jwtSecurityScheme))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
