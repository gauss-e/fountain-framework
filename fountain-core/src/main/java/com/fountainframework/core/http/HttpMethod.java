package com.fountainframework.core.http;

/**
 * HTTP request methods as defined in RFC 7231, RFC 5789.
 */
public enum HttpMethod {

    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    HEAD,
    OPTIONS,
    TRACE,
    CONNECT;

    /**
     * Resolve an HttpMethod from the method string.
     * Falls back to GET if the method is not recognized.
     */
    public static HttpMethod resolve(String method) {
        if (method == null || method.isEmpty()) {
            return GET;
        }
        return switch (method.toUpperCase()) {
            case "GET" -> GET;
            case "POST" -> POST;
            case "PUT" -> PUT;
            case "DELETE" -> DELETE;
            case "PATCH" -> PATCH;
            case "HEAD" -> HEAD;
            case "OPTIONS" -> OPTIONS;
            case "TRACE" -> TRACE;
            case "CONNECT" -> CONNECT;
            default -> throw new IllegalArgumentException("Unknown HTTP method: " + method);
        };
    }
}
