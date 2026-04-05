package com.fountainframework.core.http;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Represents an HTTP request received by the Fountain server.
 * Encapsulates all information from the raw HTTP request per RFC 7230-7235.
 */
public class FountainPoolRequest {

    private final HttpMethod method;
    private final String uri;
    private final String path;
    private final Map<String, List<String>> queryParameters;
    private final HttpVersion version;
    private final HttpHeaders headers;
    private final byte[] body;
    private final String remoteAddress;

    private FountainPoolRequest(Builder builder) {
        this.method = builder.method;
        this.uri = builder.uri;
        this.version = builder.version;
        this.headers = builder.headers;
        this.body = builder.body;
        this.remoteAddress = builder.remoteAddress;

        // Parse path and query parameters from URI
        int queryIndex = uri.indexOf('?');
        if (queryIndex >= 0) {
            this.path = uri.substring(0, queryIndex);
            this.queryParameters = parseQueryString(uri.substring(queryIndex + 1));
        } else {
            this.path = uri;
            this.queryParameters = Collections.emptyMap();
        }
    }

    public HttpMethod method() {
        return method;
    }

    public String uri() {
        return uri;
    }

    public String path() {
        return path;
    }

    public HttpVersion version() {
        return version;
    }

    public HttpHeaders headers() {
        return headers;
    }

    public byte[] body() {
        return body;
    }

    public String bodyAsString() {
        return bodyAsString(StandardCharsets.UTF_8);
    }

    public String bodyAsString(Charset charset) {
        return (body != null && body.length > 0) ? new String(body, charset) : "";
    }

    public String remoteAddress() {
        return remoteAddress;
    }

    public String queryParameter(String name) {
        List<String> values = queryParameters.get(name);
        return (values != null && !values.isEmpty()) ? values.getFirst() : null;
    }

    public List<String> queryParameters(String name) {
        return queryParameters.getOrDefault(name, Collections.emptyList());
    }

    public Map<String, List<String>> allQueryParameters() {
        return Collections.unmodifiableMap(queryParameters);
    }

    public String header(String name) {
        return headers.get(name);
    }

    public String contentType() {
        return headers.get("Content-Type");
    }

    public long contentLength() {
        String value = headers.get("Content-Length");
        return (value != null) ? Long.parseLong(value) : -1;
    }

    public boolean isKeepAlive() {
        String connection = headers.get("Connection");
        if (version == HttpVersion.HTTP_1_1) {
            return !"close".equalsIgnoreCase(connection);
        }
        return "keep-alive".equalsIgnoreCase(connection);
    }

    private static Map<String, List<String>> parseQueryString(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> params = new LinkedHashMap<>();
        for (String pair : queryString.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq > 0 ? decode(pair.substring(0, eq)) : decode(pair);
            String value = eq > 0 ? decode(pair.substring(eq + 1)) : "";
            params.computeIfAbsent(key, _ -> new ArrayList<>()).add(value);
        }
        return params;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return method + " " + uri + " " + version;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private HttpMethod method = HttpMethod.GET;
        private String uri = "/";
        private HttpVersion version = HttpVersion.HTTP_1_1;
        private HttpHeaders headers = new HttpHeaders();
        private byte[] body = new byte[0];
        private String remoteAddress = "";

        public Builder method(HttpMethod method) {
            this.method = method;
            return this;
        }

        public Builder uri(String uri) {
            this.uri = uri;
            return this;
        }

        public Builder version(HttpVersion version) {
            this.version = version;
            return this;
        }

        public Builder headers(HttpHeaders headers) {
            this.headers = headers;
            return this;
        }

        public Builder body(byte[] body) {
            this.body = body;
            return this;
        }

        public Builder remoteAddress(String remoteAddress) {
            this.remoteAddress = remoteAddress;
            return this;
        }

        public FountainPoolRequest build() {
            return new FountainPoolRequest(this);
        }
    }
}
