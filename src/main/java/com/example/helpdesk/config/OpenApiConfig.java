package com.example.helpdesk.config;

import com.example.helpdesk.dto.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.Set;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Helpdesk API",
                version = "1.0",
                description = "Ticketing API. Log in with POST /api/auth/login, then use the Authorize button."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {

    private static final Set<String> PUBLIC_PATHS = Set.of("/api/auth/register", "/api/auth/login");

    static {
        // The logged-in user comes from the token, so it must not appear as a request parameter.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(AuthenticationPrincipal.class);
    }

    // Documents the error shape once and attaches it to every operation, so controllers stay readable.
    @Bean
    public OpenApiCustomizer standardErrorResponses() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            ModelConverters.getInstance().readAll(ApiError.class)
                    .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));

            Content errorContent = new Content().addMediaType("application/json",
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError")));

            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
                boolean publicRoute = PUBLIC_PATHS.contains(path);
                boolean hasInput = operation.getRequestBody() != null
                        || (operation.getParameters() != null && !operation.getParameters().isEmpty());

                if (hasInput) {
                    addIfMissing(operation, "400", "Validation failed or the request is malformed", errorContent);
                }
                if (!publicRoute) {
                    addIfMissing(operation, "401", "Missing, invalid or expired access token", errorContent);
                    addIfMissing(operation, "403", "Your role does not allow this action", errorContent);
                    addIfMissing(operation, "404", "Not found, or not visible to you", errorContent);
                }
            }));
        };
    }

    private static void addIfMissing(Operation operation, String code, String description, Content content) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        if (!operation.getResponses().containsKey(code)) {
            operation.getResponses().addApiResponse(code,
                    new ApiResponse().description(description).content(content));
        }
    }
}