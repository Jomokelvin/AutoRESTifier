package dev.autorestifier;

import io.javalin.Javalin;
import io.javalin.http.UploadedFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class AutoRestifierApplication {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JarInspector INSPECTOR = new JarInspector();
    private static final EndpointRegistry REGISTRY = new EndpointRegistry();

    private static volatile Path uploadedJar;
    private static volatile ClassLoader uploadedClassLoader;

    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public");
            config.http.maxRequestSize = 50_000_000L;
        });

        app.get("/api/status", ctx -> ctx.json(Map.of(
                "name", "AutoRESTifier",
                "version", "0.1.0",
                "status", "ready",
                "javaVersion", Runtime.version().feature()
        )));

        app.post("/api/upload", ctx -> {
            UploadedFile file = ctx.uploadedFile("jar");
            if (file == null || file.filename() == null || !file.filename().toLowerCase().endsWith(".jar")) {
                ctx.status(400).json(Map.of("error", "Choose a .jar file using the 'jar' upload field."));
                return;
            }
            Path destination = Files.createTempFile("autorestifier-", ".jar");
            try (var input = file.content()) {
                Files.copy(input, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            uploadedJar = destination;
            uploadedClassLoader = new java.net.URLClassLoader(
                    new java.net.URL[]{destination.toUri().toURL()},
                    AutoRestifierApplication.class.getClassLoader()
            );
            REGISTRY.clear();
            ctx.json(Map.of(
                    "message", "JAR uploaded. Review discovered methods and explicitly expose only trusted methods.",
                    "filename", file.filename(),
                    "classes", INSPECTOR.inspect(destination)
            ));
        });

        app.get("/api/classes", ctx -> {
            if (uploadedJar == null) {
                ctx.status(400).json(Map.of("error", "Upload a JAR first."));
                return;
            }
            ctx.json(INSPECTOR.inspect(uploadedJar));
        });

        app.post("/api/expose", ctx -> {
            if (uploadedClassLoader == null) {
                ctx.status(400).json(Map.of("error", "Upload a JAR first."));
                return;
            }
            JsonNode body = JSON.readTree(ctx.body());
            String className = requiredText(body, "className");
            String methodName = requiredText(body, "methodName");
            String descriptor = requiredText(body, "descriptor");
            EndpointRegistry.Endpoint endpoint = REGISTRY.expose(
                    className, methodName, descriptor, uploadedClassLoader
            );
            ctx.status(201).json(endpoint.toMap());
        });

        app.get("/api/endpoints", ctx -> ctx.json(REGISTRY.allAsMaps()));

        app.post("/generated/{id}", ctx -> {
            try {
                JsonNode body = ctx.body().isBlank() ? JSON.createArrayNode() : JSON.readTree(ctx.body()).path("args");
                ctx.json(REGISTRY.invoke(ctx.pathParam("id"), body));
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("error", e.getMessage()));
            } catch (Throwable e) {
                ctx.status(500).json(Map.of("error", "Invocation failed", "detail", safeMessage(e)));
            }
        });

        app.exception(Exception.class, (e, ctx) -> {
            System.err.println("Request failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            if (!ctx.res().isCommitted()) {
                ctx.status(500).json(Map.of("error", "Request failed", "detail", safeMessage(e)));
            }
        });

        app.start("127.0.0.1", 7070);
        System.out.println("AutoRESTifier running at http://127.0.0.1:7070");
    }

    private static String requiredText(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return value.asText();
    }

    private static String safeMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }
}
