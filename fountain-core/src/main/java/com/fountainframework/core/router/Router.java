package com.fountainframework.core.router;

import com.fountainframework.core.handler.*;
import com.fountainframework.core.http.FountainPoolRequest;
import com.fountainframework.core.http.HttpMethod;
import com.fountainframework.core.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

/**
 * High-performance HTTP router with path parameters and generic typed handlers.
 * <p>
 * Three handler styles:
 * <ul>
 *   <li>{@link ContextHandler} — receives {@link FountainEntry}: {@code router.get("/users/{id}", entry -> ...)}</li>
 *   <li>{@link SimpleHandler} — no input: {@code router.get("/ping", () -> ...)}</li>
 *   <li>{@link FountainHandler} — typed body: {@code router.post("/users", User.class, user -> ...)}</li>
 * </ul>
 * <p>
 * All styles support any return type {@code O} — the {@link com.fountainframework.core.serialize.ResponseWriter}
 * auto-converts POJOs to JSON, Strings to text/plain, and passes {@link HttpResponse} through unchanged.
 * <p>
 * Handlers are adapted into a unified {@link RouteHandler} at registration time via {@link HandlerAdapter},
 * so the dispatch path has zero per-request overhead from type resolution.
 */
public class Router {

    private static final Logger log = LoggerFactory.getLogger(Router.class);

    private final Map<HttpMethod, RouteTrie> routeTries;
    private final HandlerAdapter adapter;
    private final String prefix;

    public Router(HandlerAdapter adapter) {
        this(adapter, "", new EnumMap<>(HttpMethod.class));
    }

    private Router(HandlerAdapter adapter, String prefix, Map<HttpMethod, RouteTrie> sharedTries) {
        this.adapter = adapter;
        this.prefix = prefix;
        this.routeTries = sharedTries;
    }

    // ---- ContextHandler — receives FountainEntry (path params + query params) ----

    public Router get(String path, ContextHandler<?> handler) {
        return addRoute(HttpMethod.GET, path, adapter.adapt(handler));
    }

    public Router post(String path, ContextHandler<?> handler) {
        return addRoute(HttpMethod.POST, path, adapter.adapt(handler));
    }

    public Router put(String path, ContextHandler<?> handler) {
        return addRoute(HttpMethod.PUT, path, adapter.adapt(handler));
    }

    public Router delete(String path, ContextHandler<?> handler) {
        return addRoute(HttpMethod.DELETE, path, adapter.adapt(handler));
    }

    public Router patch(String path, ContextHandler<?> handler) {
        return addRoute(HttpMethod.PATCH, path, adapter.adapt(handler));
    }

    public Router head(String path, ContextHandler<?> handler) {
        return addRoute(HttpMethod.HEAD, path, adapter.adapt(handler));
    }

    public Router options(String path, ContextHandler<?> handler) {
        return addRoute(HttpMethod.OPTIONS, path, adapter.adapt(handler));
    }

    // ---- SimpleHandler — no input parameters ----

    public Router get(String path, SimpleHandler<?> handler) {
        return addRoute(HttpMethod.GET, path, adapter.adapt(handler));
    }

    public Router post(String path, SimpleHandler<?> handler) {
        return addRoute(HttpMethod.POST, path, adapter.adapt(handler));
    }

    public Router put(String path, SimpleHandler<?> handler) {
        return addRoute(HttpMethod.PUT, path, adapter.adapt(handler));
    }

    public Router delete(String path, SimpleHandler<?> handler) {
        return addRoute(HttpMethod.DELETE, path, adapter.adapt(handler));
    }

    public Router patch(String path, SimpleHandler<?> handler) {
        return addRoute(HttpMethod.PATCH, path, adapter.adapt(handler));
    }

    public Router head(String path, SimpleHandler<?> handler) {
        return addRoute(HttpMethod.HEAD, path, adapter.adapt(handler));
    }

    public Router options(String path, SimpleHandler<?> handler) {
        return addRoute(HttpMethod.OPTIONS, path, adapter.adapt(handler));
    }

    // ---- Typed body handlers (PECS: ? super R for input, ? for output) ----

    public <R> Router post(String path, Class<R> bodyType, FountainHandler<? super R, ?> handler) {
        return addRoute(HttpMethod.POST, path, adapter.adapt(bodyType, handler));
    }

    public <R> Router put(String path, Class<R> bodyType, FountainHandler<? super R, ?> handler) {
        return addRoute(HttpMethod.PUT, path, adapter.adapt(bodyType, handler));
    }

    public <R> Router delete(String path, Class<R> bodyType, FountainHandler<? super R, ?> handler) {
        return addRoute(HttpMethod.DELETE, path, adapter.adapt(bodyType, handler));
    }

    public <R> Router patch(String path, Class<R> bodyType, FountainHandler<? super R, ?> handler) {
        return addRoute(HttpMethod.PATCH, path, adapter.adapt(bodyType, handler));
    }

    // ---- Route groups ----

    public Router group(String groupPrefix, Consumer<Router> configure) {
        Router sub = new Router(adapter, this.prefix + normalizePath(groupPrefix), this.routeTries);
        configure.accept(sub);
        return this;
    }

    // ---- Internal ----

    private Router addRoute(HttpMethod method, String path, RouteHandler handler) {
        String fullPath = prefix + normalizePath(path);
        RouteEntry entry = new RouteEntry(method, fullPath, handler);
        RouteTrie trie = routeTries.computeIfAbsent(method, _ -> new RouteTrie());
        trie.addRoute(entry.segments(), entry.isParam(), entry.paramNames(),
                entry.hasWildcard(), handler);
        log.info("Registered route: {} {}", method, fullPath);
        return this;
    }

    /**
     * Match a request and dispatch to the handler. Returns null if no route matches.
     * <p>
     * Uses a segment-level trie for O(depth) lookup instead of O(route-count) linear scan.
     * Binds the {@link FountainContext} to the current thread via {@link ScopedValue}
     * so handlers can access it via {@link FountainContext#current()}.
     */
    public HttpResponse handle(FountainPoolRequest request) throws Exception {
        RouteTrie trie = routeTries.get(request.method());
        if (trie == null) {
            return null;
        }

        String[] requestSegments = splitPath(request.path());
        RouteTrie.MatchResult result = trie.match(requestSegments);
        if (result == null) {
            return null;
        }

        FountainContext ctx = new FountainContext(request, result.params());
        return ScopedValue.where(FountainContext.scope(), ctx).call(result.handler()::handle);
    }

    static String[] splitPath(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return new String[0];
        }
        int start = path.charAt(0) == '/' ? 1 : 0;
        int end = path.charAt(path.length() - 1) == '/' ? path.length() - 1 : path.length();
        if (start >= end) {
            return new String[0];
        }

        // Count segments first to avoid ArrayList overhead
        int count = 1;
        for (int i = start; i < end; i++) {
            if (path.charAt(i) == '/') count++;
        }

        String[] segments = new String[count];
        int segIdx = 0;
        int segStart = start;
        for (int i = start; i < end; i++) {
            if (path.charAt(i) == '/') {
                segments[segIdx++] = path.substring(segStart, i);
                segStart = i + 1;
            }
        }
        segments[segIdx] = path.substring(segStart, end);
        return segments;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    public int routeCount() {
        return routeTries.values().stream().mapToInt(RouteTrie::routeCount).sum();
    }
}
