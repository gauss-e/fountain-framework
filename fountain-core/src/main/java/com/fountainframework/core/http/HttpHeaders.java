package com.fountainframework.core.http;

import java.util.*;

/**
 * Case-insensitive HTTP headers container (RFC 7230).
 * Supports multiple values per header name.
 * <p>
 * Two modes of operation:
 * <ul>
 *   <li><b>Owned</b> (default constructor): mutable, backed by a HashMap — used for building responses.</li>
 *   <li><b>Wrapped</b> ({@link #wrap(io.netty.handler.codec.http.HttpHeaders)}): read-only, zero-copy
 *       delegate to Netty's headers — used for incoming requests to avoid eager full-map copy.</li>
 * </ul>
 */
public class HttpHeaders implements Iterable<Map.Entry<String, List<String>>> {

    private final Map<String, List<String>> headers;
    private final io.netty.handler.codec.http.HttpHeaders nettyHeaders;

    public HttpHeaders() {
        this.headers = new HashMap<>();
        this.nettyHeaders = null;
    }

    private HttpHeaders(io.netty.handler.codec.http.HttpHeaders nettyHeaders) {
        this.headers = null;
        this.nettyHeaders = nettyHeaders;
    }

    /**
     * Creates a read-only wrapper around Netty's headers.
     * All reads delegate directly — no map copy, no allocation.
     */
    public static HttpHeaders wrap(io.netty.handler.codec.http.HttpHeaders nettyHeaders) {
        return new HttpHeaders(nettyHeaders);
    }

    private boolean isWrapped() {
        return nettyHeaders != null;
    }

    public HttpHeaders add(String name, String value) {
        if (isWrapped()) {
            throw new UnsupportedOperationException("Wrapped headers are read-only");
        }
        headers.computeIfAbsent(name.toLowerCase(), _ -> new ArrayList<>()).add(value);
        return this;
    }

    public HttpHeaders set(String name, String value) {
        if (isWrapped()) {
            throw new UnsupportedOperationException("Wrapped headers are read-only");
        }
        List<String> values = new ArrayList<>();
        values.add(value);
        headers.put(name.toLowerCase(), values);
        return this;
    }

    public String get(String name) {
        if (isWrapped()) {
            return nettyHeaders.get(name);
        }
        List<String> values = headers.get(name.toLowerCase());
        return (values != null && !values.isEmpty()) ? values.getFirst() : null;
    }

    public List<String> getAll(String name) {
        if (isWrapped()) {
            return nettyHeaders.getAll(name);
        }
        return headers.getOrDefault(name.toLowerCase(), Collections.emptyList());
    }

    public boolean contains(String name) {
        if (isWrapped()) {
            return nettyHeaders.contains(name);
        }
        return headers.containsKey(name.toLowerCase());
    }

    public Set<String> names() {
        if (isWrapped()) {
            return nettyHeaders.names();
        }
        return Collections.unmodifiableSet(headers.keySet());
    }

    public boolean isEmpty() {
        if (isWrapped()) {
            return nettyHeaders.isEmpty();
        }
        return headers.isEmpty();
    }

    public int size() {
        if (isWrapped()) {
            return nettyHeaders.size();
        }
        return headers.size();
    }

    @Override
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        if (isWrapped()) {
            // Materialize only when iterating (response building, logging)
            Map<String, List<String>> snapshot = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : nettyHeaders) {
                snapshot.computeIfAbsent(entry.getKey().toLowerCase(), _ -> new ArrayList<>())
                        .add(entry.getValue());
            }
            return Collections.unmodifiableMap(snapshot).entrySet().iterator();
        }
        return Collections.unmodifiableMap(headers).entrySet().iterator();
    }

    @Override
    public String toString() {
        if (isWrapped()) {
            return nettyHeaders.toString();
        }
        StringBuilder sb = new StringBuilder();
        for (var entry : headers.entrySet()) {
            for (String value : entry.getValue()) {
                sb.append(entry.getKey()).append(": ").append(value).append("\r\n");
            }
        }
        return sb.toString();
    }
}
