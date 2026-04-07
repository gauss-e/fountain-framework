package com.fountainframework.core.handler;

/**
 * Functional handler that receives a {@link RequestEntry} containing
 * path parameters, query parameters, headers, and request metadata.
 * <p>
 * Eliminates boilerplate — no static calls needed:
 * <pre>{@code
 * router.get("/users/{id}", entry -> {
 *     long id = entry.pathParamAsLong("id");
 *     return userService.findById(id);
 * });
 *
 * router.delete("/users/{id}", entry -> {
 *     userService.delete(entry.pathParamAsLong("id"));
 *     return HttpResponse.ok();
 * });
 * }</pre>
 *
 * @param <O> response output type
 * @see SimpleHandler for handlers that need no parameters at all
 * @see FountainHandler for POST/PUT handlers with typed request body
 */
@FunctionalInterface
public interface ContextHandler<O> {

    O handle(RequestEntry entry) throws Exception;
}
