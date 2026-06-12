package gr.upatras.ceid.timetable.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Τεκμηρίωση REST API (OpenAPI 3 / Swagger UI).
 *
 * - Swagger UI:  /swagger-ui.html  (redirect στο /swagger-ui/index.html)
 * - OpenAPI JSON: /v3/api-docs
 *
 * Το σχήμα bearerAuth επιτρέπει δοκιμή προστατευμένων endpoints από το UI:
 * Authorize → επικόλληση του JWT από το /api/auth/login.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ceidTimetableOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("CEID Timetabling System API")
                        .description("REST API για το σύστημα ωρολογίου προγράμματος "
                                + "και εξεταστικής του τμήματος ΜΗΥΠ (CEID), Παν. Πατρών. "
                                + "Διπλωματική εργασία.")
                        .version("v1")
                        .contact(new Contact().name("CEID, University of Patras")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components().addSecuritySchemes(securitySchemeName,
                        new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
