package com.fountainframework.core.http;

import java.util.*;

/**
 * Case-insensitive HTTP headers container (RFC 7230).
 * Supports multiple values per header name.
 */
public class HttpHeaders implements Iterable<Map.Entry<String, List<String>>> {

    private final Map<String, List<String>> headers = new HashMap<>();

    public HttpHeaders add(String name, String value) {
        headers.computeIfAbsent(name.toLowerCase(), _ -> new ArrayList<>()).add(value);
        return this;
    }

    public HttpHeaders set(String name, String value) {
        List<String> values = new ArrayList<>();
        values.add(value);
        headers.put(name.toLowerCase(), values);
        return this;
    }

    public String get(String name) {
        List<String> values = headers.get(name.toLowerCase());
        return (values != null && !values.isEmpty()) ? values.getFirst() : null;
    }

    public List<String> getAll(String name) {
        return headers.getOrDefault(name.toLowerCase(), Collections.emptyList());
    }

    public boolean contains(String name) {
        return headers.containsKey(name.toLowerCase());
    }

    public Set<String> names() {
        return Collections.unmodifiableSet(headers.keySet());
    }

    public boolean isEmpty() {
        return headers.isEmpty();
    }

    public int size() {
        return headers.size();
    }

    @Override
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        return Collections.unmodifiableMap(headers).entrySet().iterator();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (var entry : headers.entrySet()) {
            for (String value : entry.getValue()) {
                sb.append(entry.getKey()).append(": ").append(value).append("\r\n");
            }
        }
        return sb.toString();
    }
}
