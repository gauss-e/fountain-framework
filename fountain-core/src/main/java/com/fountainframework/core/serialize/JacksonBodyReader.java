package com.fountainframework.core.serialize;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Default {@link BodyReader} implementation backed by Jackson.
 */
public final class JacksonBodyReader implements BodyReader {

    private final ObjectMapper objectMapper;

    public JacksonBodyReader() {
        this(new ObjectMapper());
    }

    public JacksonBodyReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> T read(byte[] body, Class<T> type) throws Exception {
        return objectMapper.readValue(body, type);
    }
}
