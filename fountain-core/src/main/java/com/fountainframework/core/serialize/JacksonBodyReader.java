package com.fountainframework.core.serialize;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;

/**
 * Default {@link BodyReader} implementation backed by Jackson.
 * <p>
 * Prefer the constructor that accepts a shared {@link ObjectMapper} from
 * {@link ObjectMapperFactory#create()} — this avoids duplicate serializer caches.
 */
public final class JacksonBodyReader implements BodyReader {

    private final ObjectMapper objectMapper;

    /**
     * Creates a reader with a default ObjectMapper from {@link ObjectMapperFactory}.
     */
    public JacksonBodyReader() {
        this(ObjectMapperFactory.create());
    }

    /**
     * Creates a reader with a shared ObjectMapper — recommended when the same
     * instance is also passed to {@link JacksonResponseWriter}.
     */
    public JacksonBodyReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> T read(byte[] body, Class<T> type) throws Exception {
        return objectMapper.readValue(body, type);
    }

    /**
     * Zero-copy deserialization from an {@link InputStream}.
     * Jackson reads incrementally from the stream — no intermediate byte[] allocation.
     */
    @Override
    public <T> T read(InputStream body, Class<T> type) throws Exception {
        return objectMapper.readValue(body, type);
    }

    /**
     * Forces Jackson to resolve and cache the deserializer for the given type
     * at startup, so the first real request skips class introspection.
     */
    @Override
    public void warmup(Class<?> type) {
        objectMapper.constructType(type);
        // canDeserialize triggers full deserializer resolution + caching
        objectMapper.canDeserialize(objectMapper.constructType(type));
    }
}
