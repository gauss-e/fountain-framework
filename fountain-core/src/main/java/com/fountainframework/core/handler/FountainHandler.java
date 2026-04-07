package com.fountainframework.core.handler;

/**
 * Generic functional handler interface — the core abstraction for typed request processing.
 * <p>
 * Inspired by JDK's {@code Function<T, R>} design philosophy:
 * <ul>
 *   <li>{@code R} — request body type (auto-deserialized from JSON)</li>
 *   <li>{@code O} — response type (auto-serialized to JSON if not {@code HttpResponse})</li>
 * </ul>
 *
 * The framework resolves {@code R} at registration time via the provided {@code Class<R>} token
 * and automatically deserializes the request body — no annotation required.
 * Access the request context via {@link FountainContext#current()} if needed.
 *
 * <pre>{@code
 * router.post("/users", User.class, user -> {
 *     long id = userService.save(user);
 *     return new CreatedResponse(id, user.name());
 * });
 * }</pre>
 *
 * @param <R> request body type
 * @param <O> response output type
 * @see ContextHandler for handlers that receive path/query parameters
 * @see SimpleHandler for handlers that don't need any input
 */
@FunctionalInterface
public interface FountainHandler<R, O> {

    O handle(R body) throws Exception;
}
