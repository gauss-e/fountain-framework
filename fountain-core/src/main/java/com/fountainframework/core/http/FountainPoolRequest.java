package com.fountainframework.core.http;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Lazy adapter over Netty's {@link HttpRequest} and a body {@link ByteBuf}.
 * <p>
 * Instead of eagerly copying every field from the Netty request into a POJO,
 * this class delegates to the underlying Netty objects on demand. Fields like
 * {@link #path()}, {@link #body()}, and {@link #queryParameter(String)} are
 * computed and cached on first access — if the handler never touches them,
 * zero work is done.
 * <p>
 * <b>Thread safety:</b> A single {@code FountainPoolRequest} is created on the
 * Netty I/O thread and then handed off to exactly one virtual thread. Volatile
 * fields protect against the publication race between those two threads. After
 * hand-off, access is single-threaded.
 * <p>
 * Callers <b>must</b> invoke {@link #release()} after processing to return the
 * body buffer to the pool.
 */
public final class FountainPoolRequest implements FountainRequest {

    private static final byte[] EMPTY_BODY = new byte[0];

    // ---- Underlying Netty objects (never null after construction) ----
    private final HttpRequest nettyRequest;
    private final ByteBuf bodyBuf;
    private final String remoteAddr;

    // ---- Lazily derived fields ----
    private volatile String path;
    private volatile String rawQuery;
    private volatile boolean pathParsed;
    private volatile HttpMethod method;
    private volatile HttpVersion version;
    private volatile HttpHeaders wrappedHeaders;
    private volatile Map<String, List<String>> queryParameters;
    private volatile byte[] bodyBytes;

    /**
     * Wrap a Netty request and a body buffer.
     *
     * @param nettyRequest the decoded HTTP request head (method, URI, headers)
     * @param bodyBuf      the accumulated body (may be {@code EMPTY_BUFFER})
     * @param remoteAddr   pre-resolved remote IP string
     */
    public FountainPoolRequest(HttpRequest nettyRequest, ByteBuf bodyBuf, String remoteAddr) {
        this.nettyRequest = Objects.requireNonNull(nettyRequest, "nettyRequest");
        this.bodyBuf = Objects.requireNonNull(bodyBuf, "bodyBuf");
        this.remoteAddr = remoteAddr != null ? remoteAddr : "";
    }

    // ---- Request line (lazy) ----

    @Override
    public HttpMethod method() {
        HttpMethod m = method;
        if (m == null) {
            m = HttpMethod.resolve(nettyRequest.method().name());
            method = m;
        }
        return m;
    }

    @Override
    public String uri() {
        return nettyRequest.uri();
    }

    @Override
    public String path() {
        ensurePathParsed();
        return path;
    }

    @Override
    public HttpVersion version() {
        HttpVersion v = version;
        if (v == null) {
            v = HttpVersion.resolve(nettyRequest.protocolVersion().text());
            version = v;
        }
        return v;
    }

    // ---- Headers (zero-copy wrap) ----

    @Override
    public HttpHeaders headers() {
        HttpHeaders h = wrappedHeaders;
        if (h == null) {
            h = HttpHeaders.wrap(nettyRequest.headers());
            wrappedHeaders = h;
        }
        return h;
    }

    @Override
    public String header(String name) {
        return headers().get(name);
    }

    @Override
    public String contentType() {
        return nettyRequest.headers().get("Content-Type");
    }

    @Override
    public long contentLength() {
        String value = nettyRequest.headers().get("Content-Length");
        return (value != null) ? Long.parseLong(value) : -1;
    }

    // ---- Body (lazy materialization) ----

    @Override
    public byte[] body() {
        byte[] b = bodyBytes;
        if (b != null) {
            return b;
        }
        if (!bodyBuf.isReadable()) {
            b = EMPTY_BODY;
        } else {
            b = new byte[bodyBuf.readableBytes()];
            bodyBuf.getBytes(bodyBuf.readerIndex(), b);
        }
        bodyBytes = b;
        return b;
    }

    @Override
    public String bodyAsString() {
        return bodyAsString(StandardCharsets.UTF_8);
    }

    @Override
    public String bodyAsString(Charset charset) {
        byte[] b = body();
        return (b.length > 0) ? new String(b, charset) : "";
    }

    // ---- Query parameters (lazy parsing) ----

    @Override
    public String queryParameter(String name) {
        List<String> values = queryParameters().get(name);
        return (values != null && !values.isEmpty()) ? values.getFirst() : null;
    }

    @Override
    public List<String> queryParameters(String name) {
        return queryParameters().getOrDefault(name, Collections.emptyList());
    }

    @Override
    public Map<String, List<String>> allQueryParameters() {
        return Collections.unmodifiableMap(queryParameters());
    }

    // ---- Connection metadata ----

    @Override
    public String remoteAddress() {
        return remoteAddr;
    }

    @Override
    public boolean isKeepAlive() {
        return HttpUtil.isKeepAlive(nettyRequest);
    }

    // ---- Lifecycle ----

    @Override
    public void release() {
        if (bodyBuf.refCnt() > 0) {
            bodyBuf.release();
        }
    }

    // ---- Internal helpers ----

    private void ensurePathParsed() {
        if (!pathParsed) {
            String uri = nettyRequest.uri();
            int queryIndex = uri.indexOf('?');
            if (queryIndex >= 0) {
                path = uri.substring(0, queryIndex);
                rawQuery = uri.substring(queryIndex + 1);
            } else {
                path = uri;
                rawQuery = null;
            }
            pathParsed = true;
        }
    }

    private Map<String, List<String>> queryParameters() {
        Map<String, List<String>> qp = queryParameters;
        if (qp == null) {
            ensurePathParsed();
            qp = (rawQuery != null) ? parseQueryString(rawQuery) : Collections.emptyMap();
            queryParameters = qp;
        }
        return qp;
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
        return method() + " " + uri() + " " + version();
    }

    // ---- Static factory for testing / non-Netty usage ----

    /**
     * Build a request from raw values — primarily for testing.
     * For production (Netty pipeline), use the constructor directly.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private HttpMethod method = HttpMethod.GET;
        private String uri = "/";
        private HttpVersion version = HttpVersion.HTTP_1_1;
        private HttpHeaders headers = new HttpHeaders();
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

        public Builder body(byte[] body) {
            this.bodyBytes = body;
            return this;
        }

        public Builder remoteAddress(String remoteAddress) {
            this.remoteAddress = remoteAddress;
            return this;
        }

        public FountainPoolRequest build() {
            // Build a minimal Netty HttpRequest for the adapter
            io.netty.handler.codec.http.DefaultHttpRequest nettyReq =
                    new io.netty.handler.codec.http.DefaultHttpRequest(
                            io.netty.handler.codec.http.HttpVersion.valueOf(version.text()),
                            io.netty.handler.codec.http.HttpMethod.valueOf(method.name()),
                            uri
                    );
            // Copy headers into Netty's header object
            if (headers != null) {
                for (Map.Entry<String, List<String>> entry : headers) {
                    for (String value : entry.getValue()) {
                        nettyReq.headers().add(entry.getKey(), value);
                    }
                }
            }

            ByteBuf bodyBuf;
            if (bodyBytes != null && bodyBytes.length > 0) {
                bodyBuf = io.netty.buffer.Unpooled.wrappedBuffer(bodyBytes);
            } else {
                bodyBuf = io.netty.buffer.Unpooled.EMPTY_BUFFER;
            }

            return new FountainPoolRequest(nettyReq, bodyBuf, remoteAddress);
        }
    }
}
