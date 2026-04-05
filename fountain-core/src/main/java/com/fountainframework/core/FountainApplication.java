package com.fountainframework.core;

import com.fountainframework.core.config.FountainConfig;
import com.fountainframework.core.handler.ContextHandler;
import com.fountainframework.core.handler.FountainHandler;
import com.fountainframework.core.handler.HandlerAdapter;
import com.fountainframework.core.router.Router;
import com.fountainframework.core.serialize.BodyReader;
import com.fountainframework.core.serialize.JacksonBodyReader;
import com.fountainframework.core.serialize.JacksonResponseWriter;
import com.fountainframework.core.serialize.ResponseWriter;
import com.fountainframework.core.server.FountainServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Main entry point for a Fountain application.
 * Provides a Gin-style fluent API for route registration and server startup.
 *
 * <pre>{@code
 * FountainApplication app = FountainApplication.create();
 *
 * // Context-only handler
 * app.get("/hello", ctx -> HttpResponse.ok("Hello!"));
 *
 * // Typed handler — auto-deserialize body, auto-serialize response
 * app.post("/users", User.class, (user, ctx) ->
 *         new UserResponse(user.id(), user.name()));
 *
 * app.start();  // port from config or default 8080
 * }</pre>
 */
public class FountainApplication {

    private static final Logger log = LoggerFactory.getLogger(FountainApplication.class);

    private final Router router;
    private final FountainConfig config;
    private FountainServer server;

    private FountainApplication(Router router, FountainConfig config) {
        this.router = router;
        this.config = config;
    }

    /**
     * Create with default Jackson-based serialization and the given config.
     */
    public static FountainApplication create(FountainConfig config) {
        return create(config, new JacksonBodyReader(), new JacksonResponseWriter());
    }

    /**
     * Create with custom serialization — open for extension.
     */
    public static FountainApplication create(FountainConfig config, BodyReader bodyReader, ResponseWriter responseWriter) {
        HandlerAdapter adapter = new HandlerAdapter(bodyReader, responseWriter);
        Router router = new Router(adapter);
        return new FountainApplication(router, config);
    }

    // ---- Context-only handler delegates ----

    public FountainApplication get(String path, ContextHandler<?> handler) {
        router.get(path, handler);
        return this;
    }

    public FountainApplication post(String path, ContextHandler<?> handler) {
        router.post(path, handler);
        return this;
    }

    public FountainApplication put(String path, ContextHandler<?> handler) {
        router.put(path, handler);
        return this;
    }

    public FountainApplication delete(String path, ContextHandler<?> handler) {
        router.delete(path, handler);
        return this;
    }

    public FountainApplication patch(String path, ContextHandler<?> handler) {
        router.patch(path, handler);
        return this;
    }

    // ---- Typed body handler delegates ----

    public <R> FountainApplication get(String path, Class<R> bodyType, FountainHandler<? super R, ?> handler) {
        router.get(path, bodyType, handler);
        return this;
    }

    public <R> FountainApplication post(String path, Class<R> bodyType, FountainHandler<? super R, ?> handler) {
        router.post(path, bodyType, handler);
        return this;
    }

    public <R> FountainApplication put(String path, Class<R> bodyType, FountainHandler<? super R, ?> handler) {
        router.put(path, bodyType, handler);
        return this;
    }

    public <R> FountainApplication delete(String path, Class<R> bodyType, FountainHandler<? super R, ?> handler) {
        router.delete(path, bodyType, handler);
        return this;
    }

    public <R> FountainApplication patch(String path, Class<R> bodyType, FountainHandler<? super R, ?> handler) {
        router.patch(path, bodyType, handler);
        return this;
    }

    // ---- Groups ----

    public FountainApplication group(String prefix, Consumer<Router> configure) {
        router.group(prefix, configure);
        return this;
    }

    public Router router() {
        return router;
    }

    public FountainConfig config() {
        return config;
    }

    // ---- Server lifecycle ----

    /**
     * Start the server using port and virtual thread pool size from configuration.
     * Defaults: port=8080, virtualthread.num=1000.
     */
    public void start() {
        start(config.getPort());
    }

    /**
     * Start the server on the given port (overrides config).
     */
    public void start(int port) {
        int maxConcurrency = config.getMaxConcurrency();
        server = new FountainServer(port, router, maxConcurrency);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (server != null) {
                server.stop();
            }
        }));

        try {
            server.start();
            log.info("Fountain application ready — {} route(s) registered", router.routeCount());
            server.awaitTermination();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Server interrupted", e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop();
        }
    }
}
