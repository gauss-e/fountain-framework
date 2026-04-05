package com.fountainframework.core.router;

import com.fountainframework.core.handler.FountainHandler;
import com.fountainframework.core.http.HttpMethod;

/**
 * A compiled route: HTTP method + path segments + handler function.
 * No reflection involved — the handler is a direct function reference.
 */
public final class RouteEntry {

    private final HttpMethod method;
    private final String pattern;
    private final String[] segments;       // split path segments
    private final boolean[] isParam;       // true if segment is a :param
    private final String[] paramNames;     // param name for each segment (null if static)
    private final boolean hasWildcard;     // ends with *
    private final FountainHandler handler;

    public RouteEntry(HttpMethod method, String pattern, FountainHandler handler) {
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
            String[] parts = path.split("/");
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

    public FountainHandler handler() {
        return handler;
    }

    @Override
    public String toString() {
        return method + " " + pattern;
    }
}
