package com.fountainframework.core.handler;

import java.util.List;
import java.util.Map;

/**
 * Read-only view of request parameters for handler consumption.
 * <p>
 * Provides typed access to path parameters, query parameters, headers,
 * and request metadata. Implementations are passed directly to
 * {@link ContextHandler}, decoupling handlers from framework internals.
 *
 * <pre>{@code
 * router.get("/users/{id}", entry -> {
 *     long id   = entry.pathParamAsLong("id");
 *     String q  = entry.queryParam("filter", "all");
 *     String auth = entry.header("Authorization");
 *     return userService.find(id, q);
 * });
 * }</pre>
 *
 * @see FountainEntry for the default implementation
 */
public interface RequestEntry {

    // ---- Path parameters ----

    /** Single path parameter by name, e.g. {@code /users/{id}} → {@code pathParam("id")}. */
    String pathParam(String name);

    /** Path parameter parsed as {@code int}. */
    int pathParamAsInt(String name);

    /** Path parameter parsed as {@code long}. */
    long pathParamAsLong(String name);

    /** Unmodifiable map of all path parameters. */
    Map<String, String> pathParams();

    // ---- Query parameters ----

    /** First value for the query parameter, or {@code null} if absent. */
    String queryParam(String name);

    /** First value for the query parameter, or {@code defaultValue} if absent. */
    String queryParam(String name, String defaultValue);

    /** All values for a multivalued query parameter (empty list if absent). */
    List<String> queryParams(String name);

    /** Unmodifiable map of all query parameters. */
    Map<String, List<String>> queryParamMap();

    // ---- Headers ----

    /** Single header value by name (case-insensitive). */
    String header(String name);

    // ---- Request metadata ----

    /** The request path without query string, e.g. {@code /users/42}. */
    String path();

    /** The full URI including query string, e.g. {@code /users/42?foo=bar}. */
    String uri();

    /** Raw request body as a byte array. */
    byte[] body();

    /** Request body decoded as UTF-8 string. */
    String bodyAsString();
}
