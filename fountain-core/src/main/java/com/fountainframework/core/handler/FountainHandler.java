package com.fountainframework.core.handler;

import com.fountainframework.core.http.HttpResponse;

/**
 * Functional interface for handling HTTP requests.
 * This is the core abstraction — users implement this to define endpoint logic.
 *
 * <pre>{@code
 * app.get("/hello", ctx -> HttpResponse.ok("Hello!"));
 * }</pre>
 */
@FunctionalInterface
public interface FountainHandler {

    HttpResponse handle(FountainContext ctx) throws Exception;
}
