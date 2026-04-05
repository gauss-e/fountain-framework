package com.fountainframework.core.http;

/**
 * HTTP protocol versions.
 */
public enum HttpVersion {

    HTTP_1_0("HTTP/1.0"),
    HTTP_1_1("HTTP/1.1");

    private final String text;

    HttpVersion(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }

    public static HttpVersion resolve(String version) {
        if (version == null) {
            return HTTP_1_1;
        }
        return switch (version) {
            case "HTTP/1.0" -> HTTP_1_0;
            case "HTTP/1.1" -> HTTP_1_1;
            default -> HTTP_1_1;
        };
    }

    @Override
    public String toString() {
        return text;
    }
}
