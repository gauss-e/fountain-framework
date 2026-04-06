package com.fountainframework.core.handler;

import com.fountainframework.core.serialize.BodyReader;
import com.fountainframework.core.serialize.ResponseWriter;

/**
 * Adapts {@link FountainHandler} and {@link ContextHandler} into a unified {@link RouteHandler}.
 * <p>
 * Adaptation happens once at route registration time — no per-request overhead.
 * The resulting {@link RouteHandler} is a direct function call chain:
 * {@code deserialize → handler → serialize}, composed at build time.
 */
public final class HandlerAdapter {

    private final BodyReader bodyReader;
    private final ResponseWriter responseWriter;

    public HandlerAdapter(BodyReader bodyReader, ResponseWriter responseWriter) {
        this.bodyReader = bodyReader;
        this.responseWriter = responseWriter;
    }

    /**
     * Adapt a context-only handler.
     * <p>
     * Pipeline: ctx → handler.handle(ctx) → responseWriter.write(result)
     */
    public <O> RouteHandler adapt(ContextHandler<O> handler) {
        return ctx -> {
            O result = handler.handle(ctx);
            return responseWriter.write(result);
        };
    }

    /**
     * Adapt a generic typed handler with auto body deserialization.
     * <p>
     * Pipeline: ctx.body() → bodyReader.read(bodyType) → handler.handle(body, ctx) → responseWriter.write(result)
     *
     * @param bodyType the Class token for R — captured at registration time, used for deserialization
     */
    public <R, O> RouteHandler adapt(Class<R> bodyType, FountainHandler<R, O> handler) {
        return ctx -> {
            R body = bodyReader.read(ctx.body(), bodyType);
            O result = handler.handle(body, ctx);
            return responseWriter.write(result);
        };
    }
}
