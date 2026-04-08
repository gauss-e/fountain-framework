package com.fountainframework.core.http;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * Read-only view of an HTTP request.
 * <p>
 * This is the user-facing contract — handlers and framework extensions program
 * against this interface without knowing whether the backing implementation wraps
 * Netty objects, a test stub, or any other HTTP transport.
 * <p>
 * All accessors are <b>lazy</b>: the underlying data (body bytes, query parameters,
 * etc.) is only materialized from the transport layer when the caller actually
 * invokes the corresponding method. This keeps allocation proportional to what the
 * handler needs rather than the full request size.
 *
 * @see FountainPoolRequest for the Netty-backed implementation
 */
public interface FountainRequest {

    // ---- Request line ----

    /** HTTP method (GET, POST, …). */
    HttpMethod method();

    /** Full URI including query string, e.g. {@code /users/42?foo=bar}. */
    String uri();

    /** Path without query string, e.g. {@code /users/42}. */
    String path();

    /** HTTP version (HTTP/1.0, HTTP/1.1). */
    HttpVersion version();

    // ---- Headers ----

    /** All request headers. */
    HttpHeaders headers();

    /** Single header value by name (case-insensitive). */
    String header(String name);

    /** Content-Type header value, or {@code null}. */
    String contentType();

    /** Content-Length header value, or {@code -1} if absent. */
    long contentLength();

    // ---- Body ----

    /**
     * Returns the request body as a byte array.
     * <p>
     * Materialized lazily on first call. GET / no-body requests return an
     * empty array with zero allocation.
     */
    byte[] body();

    /** Request body decoded as UTF-8 string. */
    String bodyAsString();

    /** Request body decoded with the given charset. */
    String bodyAsString(Charset charset);

    // ---- Query parameters ----

    /** First value for the query parameter, or {@code null} if absent. */
    String queryParameter(String name);

    /** All values for a multi-valued query parameter (empty list if absent). */
    List<String> queryParameters(String name);

    /** Unmodifiable map of all query parameters. */
    Map<String, List<String>> allQueryParameters();

    // ---- Connection metadata ----

    /** Remote address (IP) of the client. */
    String remoteAddress();

    /** Whether the connection should be kept alive. */
    boolean isKeepAlive();

    // ---- Lifecycle ----

    /**
     * Release any underlying transport resources (e.g. Netty ByteBuf).
     * Must be called after handler processing to avoid resource leaks.
     */
    void release();
}
