package com.fountainframework.core.handler;

import com.fountainframework.core.http.FountainRequest;

import java.util.Map;

/**
 * Request context bound to the current virtual thread via {@link ScopedValue}.
 * <p>
 * Similar to Spring's {@code RequestContextHolder}. Provides:
 * <ul>
 *   <li>{@link #entry()} — the {@link RequestEntry} for handler parameter access</li>
 *   <li>{@link #request()} — the {@link FountainRequest} for framework internals</li>
 * </ul>
 * <p>
 * Day-to-day handler code works through the {@link RequestEntry} interface
 * (passed directly to {@link ContextHandler}). This class is reserved for
 * framework-level concerns: ScopedValue binding, body access for deserialization,
 * and future extensions (middleware, attributes, etc.).
 */
public final class FountainContext {

    private static final ScopedValue<FountainContext> SCOPE = ScopedValue.newInstance();

    private final FountainEntry entry;

    public FountainContext(FountainRequest request, Map<String, String> pathParams) {
        this.entry = new FountainEntry(request, pathParams);
    }

    /**
     * Returns the context bound to the current virtual thread.
     *
     * @throws java.util.NoSuchElementException if called outside a request scope
     */
    public static FountainContext current() {
        return SCOPE.get();
    }

    /**
     * Returns the {@link ScopedValue} carrier — used internally by the router
     * to bind the context before handler execution.
     */
    public static ScopedValue<FountainContext> scope() {
        return SCOPE;
    }

    /**
     * The {@link RequestEntry} providing typed access to path params, query params,
     * headers, and request metadata. This is what {@link ContextHandler} receives.
     */
    public RequestEntry entry() {
        return entry;
    }

    /**
     * Raw request body — used internally by {@link HandlerAdapter} for deserialization.
     */
    public byte[] body() {
        return entry.body();
    }

    /**
     * The underlying request object — for framework-internal use.
     */
    public FountainRequest request() {
        return entry.getRequest();
    }
}
