package com.fountainframework.core.serialize;

import java.io.InputStream;

/**
 * Deserializes a request body into a typed object.
 * <p>
 * Single responsibility: byte[] → T conversion.
 * Open for extension — implement this interface to support formats beyond JSON.
 */
public interface BodyReader {

    <T> T read(byte[] body, Class<T> type) throws Exception;

    /**
     * Deserialize from an {@link InputStream} — allows zero-copy reads when the
     * transport provides a stream view over its buffer (e.g. Netty ByteBufInputStream).
     * <p>
     * Default implementation falls back to {@link #read(byte[], Class)} by draining the stream.
     */
    default <T> T read(InputStream body, Class<T> type) throws Exception {
        return read(body.readAllBytes(), type);
    }

    /**
     * Pre-warm internal caches (serializer lookups, type metadata) for the given type.
     * Called at route registration time so the first real request avoids cold-start overhead.
     * <p>
     * Default is a no-op — implementations that benefit from warmup (e.g. Jackson) override this.
     */
    default void warmup(Class<?> type) {}
}
