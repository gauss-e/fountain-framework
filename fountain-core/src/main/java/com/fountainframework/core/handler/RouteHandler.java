package com.fountainframework.core.handler;

import com.fountainframework.core.http.HttpResponse;

/**
 * Internal unified handler interface used by the routing/dispatch pipeline.
 * <p>
 * Both {@link FountainHandler} and {@link ContextHandler} are adapted into this
 * form at registration time via {@link HandlerAdapter}, so the dispatch layer
 * only deals with one handler type — no branching per request.
 */
@FunctionalInterface
public interface RouteHandler {

    HttpResponse handle(FountainContext ctx) throws Exception;
}
