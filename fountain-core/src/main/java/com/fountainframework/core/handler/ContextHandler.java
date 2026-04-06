package com.fountainframework.core.handler;

/**
 * Functional handler that operates on the request context directly, without typed body binding.
 * <p>
 * Analogous to JDK's {@code Supplier<T>} / {@code Function<Context, O>} — takes context, produces output.
 * Use this for routes where you only need path params, query params, or raw body access.
 *
 * <pre>{@code
 * // Returns HttpResponse directly
 * router.get("/hello", ctx -> HttpResponse.ok("Hello!"));
 *
 * // Returns a POJO — auto-serialized to JSON
 * router.get("/users", ctx -> List.copyOf(users.values()));
 * }</pre>
 *
 * @param <O> response output type
 * @see FountainHandler for handlers with typed request body deserialization
 */
@FunctionalInterface
public interface ContextHandler<O> {

    O handle(FountainContext ctx) throws Exception;
}
