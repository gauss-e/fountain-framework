package com.fountainframework.core.handler;

import com.fountainframework.core.http.HttpResponse;

/**
 * Internal unified handler interface used by the routing/dispatch pipeline.
 * <p>
 * {@link FountainHandler}, {@link ContextHandler} and {@link SimpleHandler} are adapted into this
 * form at registration time via {@link HandlerAdapter}, so the dispatch layer
 * only deals with one handler type — no branching per request.
 * <p>
 * The {@link FountainContext} is available via {@link FountainContext#current()}
 * (bound by the router before invocation).
 */
@FunctionalInterface
public interface RouteHandler {

    HttpResponse handle() throws Exception;
}
