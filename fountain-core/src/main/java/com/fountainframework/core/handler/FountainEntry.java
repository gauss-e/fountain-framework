package com.fountainframework.core.handler;

import com.fountainframework.core.http.FountainRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link RequestEntry}.
 * <p>
 * Holds a {@link FountainRequest} and extracted path parameters,
 * providing the single point of access for all request data in handlers.
 * <p>
 * {@link FountainContext} retains a reference for framework-level concerns
 * (ScopedValue binding, future middleware, etc.) but day-to-day handler
 * code works exclusively through the {@link RequestEntry} interface.
 */
public final class FountainEntry implements RequestEntry {

    private final FountainRequest request;
    private final Map<String, String> pathParams;

    public FountainEntry(FountainRequest request, Map<String, String> pathParams) {
        this.request = request;
        this.pathParams = pathParams != null ? pathParams : Collections.emptyMap();
    }

    FountainRequest getRequest() {
        return request;
    }

    // ---- Path parameters ----

    @Override
    public String pathParam(String name) {
        return pathParams.get(name);
    }

    @Override
    public int pathParamAsInt(String name) {
        return Integer.parseInt(pathParams.get(name));
    }

    @Override
    public long pathParamAsLong(String name) {
        return Long.parseLong(pathParams.get(name));
    }

    @Override
    public Map<String, String> pathParams() {
        return Collections.unmodifiableMap(pathParams);
    }

    // ---- Query parameters ----

    @Override
    public String queryParam(String name) {
        return request.queryParameter(name);
    }

    @Override
    public String queryParam(String name, String defaultValue) {
        String value = request.queryParameter(name);
        return value != null ? value : defaultValue;
    }

    @Override
    public List<String> queryParams(String name) {
        return request.queryParameters(name);
    }

    @Override
    public Map<String, List<String>> queryParamMap() {
        return request.allQueryParameters();
    }

    // ---- Headers ----

    @Override
    public String header(String name) {
        return request.header(name);
    }

    // ---- Request metadata ----

    @Override
    public String path() {
        return request.path();
    }

    @Override
    public String uri() {
        return request.uri();
    }

    @Override
    public byte[] body() {
        return request.body();
    }

    @Override
    public String bodyAsString() {
        return request.bodyAsString();
    }
}
