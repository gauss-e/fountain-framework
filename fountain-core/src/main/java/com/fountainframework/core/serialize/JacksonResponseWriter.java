package com.fountainframework.core.serialize;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fountainframework.core.http.HttpResponse;

/**
 * Default {@link ResponseWriter} implementation backed by Jackson.
 * <p>
 * Conversion rules:
 * <ul>
 *   <li>{@code HttpResponse} → pass through unchanged</li>
 *   <li>{@code String} → 200 text/plain</li>
 *   <li>Any other object → serialize to JSON, 200 application/json</li>
 *   <li>{@code null} → 204 No Content</li>
 * </ul>
 * <p>
 * Prefer the constructor that accepts a shared {@link ObjectMapper} from
 * {@link ObjectMapperFactory#create()} — this avoids duplicate serializer caches.
 */
public final class JacksonResponseWriter implements ResponseWriter {

    private final ObjectMapper objectMapper;

    /**
     * Creates a writer with a default ObjectMapper from {@link ObjectMapperFactory}.
     */
    public JacksonResponseWriter() {
        this(ObjectMapperFactory.create());
    }

    /**
     * Creates a writer with a shared ObjectMapper — recommended when the same
     * instance is also passed to {@link JacksonBodyReader}.
     */
    public JacksonResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Forces Jackson to resolve and cache the serializer for the given type
     * at startup, so the first real response skips class introspection.
     */
    @Override
    public void warmup(Class<?> type) {
        objectMapper.canSerialize(type);
    }

    @Override
    public HttpResponse write(Object result) throws Exception {
        if (result instanceof HttpResponse response) {
            return response;
        }
        if (result == null) {
            return new HttpResponse(204);
        }
        if (result instanceof String s) {
            return HttpResponse.ok(s);
        }
        String json = objectMapper.writeValueAsString(result);
        return HttpResponse.json(json);
    }
}
