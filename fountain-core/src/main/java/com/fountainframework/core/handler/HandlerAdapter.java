package com.fountainframework.core.handler;

import com.fountainframework.core.serialize.BodyReader;
import com.fountainframework.core.serialize.ResponseWriter;

/**
 * Adapts {@link ContextHandler}, {@link SimpleHandler} and {@link FountainHandler}
 * into a unified {@link RouteHandler}.
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
     * Adapt a context handler.
     * <p>
     * Pipeline: FountainContext.current().entry() → handler.handle(entry) → responseWriter.write(result)
     * <p>
     * The {@link RequestEntry} is extracted from the current context and passed directly
     * to the handler — no boilerplate needed.
     */
    public RouteHandler adapt(ContextHandler<?> handler) {
        return () -> {
            Object result = handler.handle(FountainContext.current().entry());
            return responseWriter.write(result);
        };
    }

    /**
     * Adapt a simple handler (no input parameters).
     * <p>
     * Pipeline: handler.handle() → responseWriter.write(result)
     * <p>
     * The handler may optionally access the request context via {@link FountainContext#current()}.
     */
    public RouteHandler adapt(SimpleHandler<?> handler) {
        return () -> {
            Object result = handler.handle();
            return responseWriter.write(result);
        };
    }

    /**
     * Adapt a generic typed handler with auto body deserialization.
     * <p>
     * Follows PECS (Producer Extends, Consumer Super):
     * <ul>
     *   <li>{@code ? super R} — handler can accept R or any supertype (contravariant input).
     *       e.g. {@code FountainHandler<BaseRequest, ?>} works when registered with {@code UserRequest.class}
     *       where {@code UserRequest extends BaseRequest}.</li>
     *   <li>{@code ?} — handler may return any type (covariant output).
     *       e.g. handler returning {@code AdminResponse} works when declared as producing {@code BaseResponse}.</li>
     * </ul>
     *
     * @param bodyType the Class token for R — captured at registration time, used for deserialization
     */
    public <R> RouteHandler adapt(Class<R> bodyType, FountainHandler<? super R, ?> handler) {
        // Warmup: trigger serializer/deserializer cache population at registration time,
        // so the first real request to this route avoids Jackson's cold-start introspection.
        bodyReader.warmup(bodyType);

        return () -> {
            // Use InputStream path — reads directly from the underlying ByteBuf
            // without materializing the full body into a byte[].
            R body = bodyReader.read(FountainContext.current().request().bodyAsStream(), bodyType);
            Object result = handler.handle(body);
            return responseWriter.write(result);
        };
    }
}
