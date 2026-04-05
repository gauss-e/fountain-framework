package com.fountainframework.core.http;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Represents an HTTP response to be sent back to the client.
 */
public class HttpResponse {

    private int statusCode;
    private final HttpHeaders headers;
    private byte[] body;

    public HttpResponse() {
        this.statusCode = 200;
        this.headers = new HttpHeaders();
        this.body = new byte[0];
    }

    public HttpResponse(int statusCode) {
        this.statusCode = statusCode;
        this.headers = new HttpHeaders();
        this.body = new byte[0];
    }

    public int statusCode() {
        return statusCode;
    }

    public HttpResponse statusCode(int statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public HttpHeaders headers() {
        return headers;
    }

    public HttpResponse header(String name, String value) {
        headers.set(name, value);
        return this;
    }

    public byte[] body() {
        return body;
    }

    public HttpResponse body(byte[] body) {
        this.body = body;
        return this;
    }

    public HttpResponse body(String body) {
        return body(body, StandardCharsets.UTF_8);
    }

    public HttpResponse body(String body, Charset charset) {
        this.body = body.getBytes(charset);
        return this;
    }

    public HttpResponse contentType(String contentType) {
        headers.set("Content-Type", contentType);
        return this;
    }

    public static HttpResponse ok() {
        return new HttpResponse(200);
    }

    public static HttpResponse ok(String body) {
        return new HttpResponse(200).body(body).contentType("text/plain; charset=UTF-8");
    }

    public static HttpResponse json(String json) {
        return new HttpResponse(200).body(json).contentType("application/json; charset=UTF-8");
    }

    public static HttpResponse notFound() {
        return new HttpResponse(404).body("Not Found").contentType("text/plain; charset=UTF-8");
    }

    public static HttpResponse badRequest() {
        return new HttpResponse(400).body("Bad Request").contentType("text/plain; charset=UTF-8");
    }

    public static HttpResponse error(String message) {
        return new HttpResponse(500).body(message).contentType("text/plain; charset=UTF-8");
    }
}
