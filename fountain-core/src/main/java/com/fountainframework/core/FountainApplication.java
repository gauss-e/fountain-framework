package com.fountainframework.core;

import com.fountainframework.core.handler.FountainHandler;
import com.fountainframework.core.router.Router;
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
 * app.get("/hello", ctx -> HttpResponse.ok("Hello!"));
 *
 * app.get("/users/:id", ctx -> {
 *     long id = ctx.pathParamAsLong("id");
 *     return HttpResponse.json("{\"id\":" + id + "}");
 * });
 *
 * app.post("/users", ctx -> {
 *     User user = ctx.bodyAs(User.class);
 *     return HttpResponse.json(mapper.writeValueAsString(user));
 * });
 *
 * app.start(8080);
 * }</pre>
 */
public class FountainApplication {

    private static final Logger log = LoggerFactory.getLogger(FountainApplication.class);

    private final Router router = new Router();
    private FountainServer server;

    private FountainApplication() {}

    public static FountainApplication create() {
        return new FountainApplication();
    }

    // ---- Delegate route methods to Router ----

    public FountainApplication get(String path, FountainHandler handler) {
        router.get(path, handler);
        return this;
    }

    public FountainApplication post(String path, FountainHandler handler) {
        router.post(path, handler);
        return this;
    }

    public FountainApplication put(String path, FountainHandler handler) {
        router.put(path, handler);
        return this;
    }

    public FountainApplication delete(String path, FountainHandler handler) {
        router.delete(path, handler);
        return this;
    }

    public FountainApplication patch(String path, FountainHandler handler) {
        router.patch(path, handler);
        return this;
    }

    public FountainApplication head(String path, FountainHandler handler) {
        router.head(path, handler);
        return this;
    }

    public FountainApplication options(String path, FountainHandler handler) {
        router.options(path, handler);
        return this;
    }

    public FountainApplication any(String path, FountainHandler handler) {
        router.any(path, handler);
        return this;
    }

    /**
     * Create a route group with a common prefix.
     */
    public FountainApplication group(String prefix, Consumer<Router> configure) {
        router.group(prefix, configure);
        return this;
    }

    /**
     * Access the underlying router for advanced configuration.
     */
    public Router router() {
        return router;
    }

    /**
     * Start the HTTP server on the given port. Blocks until shutdown.
     */
    public void start(int port) {
        server = new FountainServer(port, router);

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
