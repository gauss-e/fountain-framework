package com.fountainframework.core.handler;

import com.fountainframework.core.http.FountainPoolRequest;
import com.fountainframework.core.http.HttpHeaders;
import com.fountainframework.core.http.HttpMethod;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Request context passed to every handler.
 * Provides access to path parameters, query parameters, headers, body,
 * and JSON deserialization — all without reflection.
 */
public final class FountainContext {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FountainPoolRequest request;
    private final Map<String, String> pathParams;

    public FountainContext(FountainPoolRequest request, Map<String, String> pathParams) {
        this.request = request;
        this.pathParams = pathParams != null ? pathParams : Collections.emptyMap();
    }

    // ---- Path parameters ----

    public String pathParam(String name) {
        return pathParams.get(name);
    }

    public int pathParamAsInt(String name) {
        return Integer.parseInt(pathParams.get(name));
    }

    public long pathParamAsLong(String name) {
        return Long.parseLong(pathParams.get(name));
    }

    // ---- Query parameters ----

    public String queryParam(String name) {
        return request.queryParameter(name);
    }

    public String queryParam(String name, String defaultValue) {
        String value = request.queryParameter(name);
        return value != null ? value : defaultValue;
    }

    public List<String> queryParams(String name) {
        return request.queryParameters(name);
    }

    // ---- Headers ----

    public String header(String name) {
        return request.header(name);
    }

    public HttpHeaders headers() {
        return request.headers();
    }

    public String contentType() {
        return request.contentType();
    }

    // ---- Body ----

    public byte[] body() {
        return request.body();
    }

    public String bodyAsString() {
        return request.bodyAsString();
    }

    /**
     * Deserialize the JSON request body into the given type.
     * Uses Jackson ObjectMapper — zero reflection at dispatch time,
     * Jackson internally uses optimized bytecode generation.
     */
    public <T> T bodyAs(Class<T> type) throws Exception {
        return OBJECT_MAPPER.readValue(request.body(), type);
    }

    // ---- Request metadata ----

    public HttpMethod method() {
        return request.method();
    }

    public String path() {
        return request.path();
    }

    public String uri() {
        return request.uri();
    }

    public String remoteAddress() {
        return request.remoteAddress();
    }

    public boolean isKeepAlive() {
        return request.isKeepAlive();
    }

    /**
     * Access the underlying raw request object.
     */
    public FountainPoolRequest request() {
        return request;
    }
}
