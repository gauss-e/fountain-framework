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
 */
public final class JacksonResponseWriter implements ResponseWriter {

    private final ObjectMapper objectMapper;

    public JacksonResponseWriter() {
        this(new ObjectMapper());
    }

    public JacksonResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
