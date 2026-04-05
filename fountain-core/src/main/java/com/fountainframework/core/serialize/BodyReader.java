package com.fountainframework.core.serialize;

/**
 * Deserializes a request body into a typed object.
 * <p>
 * Single responsibility: byte[] → T conversion.
 * Open for extension — implement this interface to support formats beyond JSON.
 */
public interface BodyReader {

    <T> T read(byte[] body, Class<T> type) throws Exception;
}
