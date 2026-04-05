package com.fountainframework.core;

import com.fountainframework.core.routing.RouteRegistry;
import com.fountainframework.core.server.FountainServer;
import com.fountainframework.core.server.RequestDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for a Fountain application.
 * Register handler objects and start the HTTP server.
 *
 * <pre>{@code
 * FountainApplication.create()
 *     .register(new UserController())
 *     .start(8080);
 * }</pre>
 */
public class FountainApplication {

    private static final Logger log = LoggerFactory.getLogger(FountainApplication.class);

    private final RouteRegistry registry = new RouteRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private FountainServer server;

    private FountainApplication() {}

    public static FountainApplication create() {
        return new FountainApplication();
    }

    /**
     * Register one or more handler objects whose annotated methods
     * will be scanned for route mappings.
     */
    public FountainApplication register(Object... handlers) {
        for (Object handler : handlers) {
            registry.register(handler);
        }
        return this;
    }

    /**
     * Start the HTTP server on the given port. Blocks until the server shuts down.
     */
    public void start(int port) {
        RequestDispatcher dispatcher = new RequestDispatcher(registry, objectMapper);
        server = new FountainServer(port, dispatcher);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (server != null) {
                server.stop();
            }
        }));

        try {
            server.start();
            log.info("Fountain application ready — {} route(s) registered", registry.allRoutes().size());
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
