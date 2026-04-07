package com.fountainframework.core.handler;

/**
 * Functional handler that operates without any input parameter.
 * <p>
 * Use this for routes where no path params, query params, or request body are needed,
 * or when you prefer to access the context manually via {@link FountainContext#current()}.
 *
 * <pre>{@code
 * // Returns HttpResponse directly
 * router.get("/hello", () -> HttpResponse.ok("Hello!"));
 *
 * // Returns a POJO — auto-serialized to JSON
 * router.post("/ping", () -> Map.of("status", "pong"));
 * }</pre>
 *
 * @param <O> response output type
 * @see ContextHandler for handlers that receive path/query parameters via {@link FountainEntry}
 * @see FountainHandler for handlers with typed request body deserialization
 */
@FunctionalInterface
public interface SimpleHandler<O> {

    O handle() throws Exception;
}
