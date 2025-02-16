package com.webqa.swagger;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class SwaggerServer {
    private static final int[] PORTS = {9000, 9001, 9002, 9003, 9004};
    private static final String SWAGGER_UI_HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <title>API Documentation</title>
                <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui.css" />
            </head>
            <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui-bundle.js" crossorigin></script>
                <script>
                    window.onload = () => {
                        window.ui = SwaggerUIBundle({
                            url: '/openapi.json',
                            dom_id: '#swagger-ui',
                            presets: [
                                SwaggerUIBundle.presets.apis,
                                SwaggerUIBundle.SwaggerUIStandalonePreset
                            ],
                        });
                    };
                </script>
            </body>
            </html>
            """;

    public static void main(String[] args) {
        for (int port : PORTS) {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

                // Serve Swagger UI HTML
                server.createContext("/", exchange -> {
                    byte[] content = SWAGGER_UI_HTML.getBytes();
                    exchange.getResponseHeaders().add("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, content.length);
                    exchange.getResponseBody().write(content);
                    exchange.close();
                });

                // Serve OpenAPI spec
                server.createContext("/openapi.json", exchange -> {
                    try {
                        byte[] specBytes = Files.readAllBytes(Paths.get("src/main/resources/petstore-openapi.json"));
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                        exchange.sendResponseHeaders(200, specBytes.length);
                        exchange.getResponseBody().write(specBytes);
                    } catch (IOException e) {
                        e.printStackTrace();
                        exchange.sendResponseHeaders(500, -1);
                    } finally {
                        exchange.close();
                    }
                });

                server.start();
                System.out.println("Swagger UI server started at http://localhost:" + port);
                System.out.println("Press Ctrl+C to stop the server");
                return;
            } catch (IOException e) {
                System.out.println("Port " + port + " is in use, trying next port...");
            }
        }
        System.err.println("Could not start server on any of the ports: " + Arrays.toString(PORTS));
    }
}
