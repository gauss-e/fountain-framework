package com.fountainframework.core.serialize;

import com.fountainframework.core.http.HttpResponse;

/**
 * Converts a handler's return value into an {@link HttpResponse}.
 * <p>
 * Single responsibility: O → HttpResponse conversion.
 * Open for extension — implement this to customize response serialization.
 */
public interface ResponseWriter {

    HttpResponse write(Object result) throws Exception;
}
