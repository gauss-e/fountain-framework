package com.fountainframework.core.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Represents an HTTP request received by the Fountain server.
 * Encapsulates all information from the raw HTTP request per RFC 7230-7235.
 * <p>
 * <b>Zero-copy optimizations:</b>
 * <ul>
 *   <li>Body is held as a Netty {@link ByteBuf} and only materialized to {@code byte[]}
 *       on first access via {@link #body()}. GET requests and other no-body requests
 *       never allocate a body array.</li>
 *   <li>Headers can wrap Netty's headers directly via {@link HttpHeaders#wrap} —
 *       see {@link FountainPoolRequest.Builder#headers(HttpHeaders)}.</li>
 * </ul>
 * <p>
 * When the request holds a retained {@link ByteBuf}, callers <b>must</b> invoke
 * {@link #release()} after processing to return the buffer to the pool.
 */
public class FountainPoolRequest {

    private static final byte[] EMPTY_BODY = new byte[0];

    private final HttpMethod method;
    private final String uri;
    private final String path;
    private final String rawQuery;
    private volatile Map<String, List<String>> queryParameters;
    private final HttpVersion version;
    private final HttpHeaders headers;
    private final String remoteAddress;

    /** Netty ByteBuf — may be null (no body) or EMPTY_BUFFER. */
    private final ByteBuf bodyBuf;
    /** Lazily materialized from bodyBuf on first access. */
    private byte[] bodyBytes;

    private FountainPoolRequest(Builder builder) {
        this.method = builder.method;
        this.uri = builder.uri;
        this.version = builder.version;
        this.headers = builder.headers;
        this.remoteAddress = builder.remoteAddress;
        this.bodyBuf = builder.bodyBuf;
        this.bodyBytes = builder.bodyBytes;

        // Only split path from query — defer query parsing until first access
        int queryIndex = uri.indexOf('?');
        if (queryIndex >= 0) {
            this.path = uri.substring(0, queryIndex);
            this.rawQuery = uri.substring(queryIndex + 1);
        } else {
            this.path = uri;
            this.rawQuery = null;
        }
    }

    private Map<String, List<String>> queryParameters() {
        if (queryParameters == null) {
            queryParameters = (rawQuery != null) ? parseQueryString(rawQuery) : Collections.emptyMap();
        }
        return queryParameters;
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

    /**
     * Returns the request body as a byte array.
     * <p>
     * If the request was constructed with a {@link ByteBuf}, the array is
     * materialized lazily on first call and cached for subsequent calls.
     * GET / no-body requests return an empty array with zero allocation.
     */
    public byte[] body() {
        if (bodyBytes != null) {
            return bodyBytes;
        }
        if (bodyBuf == null || !bodyBuf.isReadable()) {
            bodyBytes = EMPTY_BODY;
        } else {
            bodyBytes = new byte[bodyBuf.readableBytes()];
            bodyBuf.getBytes(bodyBuf.readerIndex(), bodyBytes);
        }
        return bodyBytes;
    }

    public String bodyAsString() {
        return bodyAsString(StandardCharsets.UTF_8);
    }

    public String bodyAsString(Charset charset) {
        byte[] b = body();
        return (b.length > 0) ? new String(b, charset) : "";
    }

    /**
     * Releases the underlying {@link ByteBuf} if one is held.
     * Must be called after handler processing to avoid buffer leaks.
     */
    public void release() {
        if (bodyBuf != null && bodyBuf.refCnt() > 0) {
            bodyBuf.release();
        }
    }

    public String remoteAddress() {
        return remoteAddress;
    }

    public String queryParameter(String name) {
        List<String> values = queryParameters().get(name);
        return (values != null && !values.isEmpty()) ? values.getFirst() : null;
    }

    public List<String> queryParameters(String name) {
        return queryParameters().getOrDefault(name, Collections.emptyList());
    }

    public Map<String, List<String>> allQueryParameters() {
        return Collections.unmodifiableMap(queryParameters());
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
        private ByteBuf bodyBuf;
        private byte[] bodyBytes;
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

        /**
         * Set the body as a retained {@link ByteBuf}. The caller must have already
         * called {@link ByteBuf#retain()} if the buffer comes from a Netty pipeline.
         * The byte array will be materialized lazily on first {@link #body()} call.
         */
        public Builder bodyBuf(ByteBuf bodyBuf) {
            this.bodyBuf = bodyBuf;
            return this;
        }

        /**
         * Set the body as a pre-materialized byte array (for testing or non-Netty usage).
         */
        public Builder body(byte[] body) {
            this.bodyBytes = body;
            this.bodyBuf = null;
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
