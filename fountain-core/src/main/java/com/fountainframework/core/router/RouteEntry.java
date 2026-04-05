package com.fountainframework.core.router;

import com.fountainframework.core.handler.RouteHandler;
import com.fountainframework.core.http.HttpMethod;

/**
 * A compiled route: HTTP method + path segments + unified handler.
 * <p>
 * The handler is always a pre-adapted {@link RouteHandler} — serialization
 * and deserialization are baked in at registration time, not resolved per request.
 */
public final class RouteEntry {

    private final HttpMethod method;
    private final String pattern;
    private final String[] segments;
    private final boolean[] isParam;
    private final String[] paramNames;
    private final boolean hasWildcard;
    private final RouteHandler handler;

    public RouteEntry(HttpMethod method, String pattern, RouteHandler handler) {
        this.method = method;
        this.pattern = pattern;
        this.handler = handler;

        String path = pattern.startsWith("/") ? pattern.substring(1) : pattern;
        this.hasWildcard = path.endsWith("*");
        if (hasWildcard) {
            path = path.substring(0, path.length() - 1);
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
        }

        if (path.isEmpty()) {
            this.segments = new String[0];
            this.isParam = new boolean[0];
            this.paramNames = new String[0];
        } else {
            String[] parts = Router.splitPath(path);
            this.segments = new String[parts.length];
            this.isParam = new boolean[parts.length];
            this.paramNames = new String[parts.length];
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].startsWith(":")) {
                    isParam[i] = true;
                    paramNames[i] = parts[i].substring(1);
                    segments[i] = null;
                } else {
                    isParam[i] = false;
                    paramNames[i] = null;
                    segments[i] = parts[i];
                }
            }
        }
    }

    public HttpMethod method() {
        return method;
    }

    public String pattern() {
        return pattern;
    }

    public String[] segments() {
        return segments;
    }

    public boolean[] isParam() {
        return isParam;
    }

    public String[] paramNames() {
        return paramNames;
    }

    public boolean hasWildcard() {
        return hasWildcard;
    }

    public RouteHandler handler() {
        return handler;
    }

    @Override
    public String toString() {
        return method + " " + pattern;
    }
}
