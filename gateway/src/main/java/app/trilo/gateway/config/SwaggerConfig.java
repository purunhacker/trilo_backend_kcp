package app.trilo.gateway.config;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class SwaggerConfig {

  private static final String SWAGGER_UI_HTML =
      """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Trilo API Documentation</title>
                <link rel="stylesheet" href="/webjars/swagger-ui/swagger-ui.css">
                <style>
                    body { margin: 0; padding: 0; }
                    .swagger-ui .topbar { display: none; }
                </style>
            </head>
            <body>
                <div id="swagger-ui"></div>
                <script src="/webjars/swagger-ui/swagger-ui-bundle.js"></script>
                <script src="/webjars/swagger-ui/swagger-ui-standalone-preset.js"></script>
                <script>
                    window.onload = function() {
                        SwaggerUIBundle({
                            url: "/api/docs/openapi.yaml",
                            dom_id: '#swagger-ui',
                            presets: [
                                SwaggerUIBundle.presets.apis,
                                SwaggerUIStandalonePreset
                            ],
                            layout: "StandaloneLayout",
                            deepLinking: true,
                            persistAuthorization: true
                        });
                    };
                </script>
            </body>
            </html>
            """;

  @Bean
  public RouterFunction<ServerResponse> swaggerRoutes() {
    return RouterFunctions.route()
        .GET(
            "/api/docs",
            request -> ServerResponse.temporaryRedirect(URI.create("/api/docs/")).build())
        .GET(
            "/api/docs/",
            request ->
                ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValue(SWAGGER_UI_HTML))
        .resources("/webjars/**", new ClassPathResource("META-INF/resources/webjars/"))
        .resources("/api/docs/**", new ClassPathResource("static/docs/"))
        .build();
  }
}
