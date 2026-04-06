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
 * High-performance HTTP router with Gin-style path parameters and generic typed handlers.
 * <p>
 * Two handler styles:
 * <ul>
 *   <li>{@link ContextHandler} — context-only: {@code router.get("/hello", ctx -> ...)}</li>
 *   <li>{@link FountainHandler} — typed body: {@code router.post("/users", User.class, (user, ctx) -> ...)}</li>
 * </ul>
 * <p>
 * Both styles support any return type {@code O} — the {@link com.fountainframework.core.serialize.ResponseWriter}
 * auto-converts POJOs to JSON, Strings to text/plain, and passes {@link HttpResponse} through unchanged.
 * <p>
 * Handlers are adapted into a unified {@link RouteHandler} at registration time via {@link HandlerAdapter},
 * so the dispatch path has zero per-request overhead from type resolution.
 */
public class Router {

    private static final Logger log = LoggerFactory.getLogger(Router.class);

    private final Map<HttpMethod, List<RouteEntry>> routeTable = new EnumMap<>(HttpMethod.class);
    private final HandlerAdapter adapter;
    private final String prefix;

    public Router(HandlerAdapter adapter) {
        this(adapter, "");
    }

    private Router(HandlerAdapter adapter, String prefix) {
        this.adapter = adapter;
        this.prefix = prefix;
    }

    // ---- Context-only handlers (no body deserialization) ----

    public <O> Router get(String path, ContextHandler<O> handler) {
        return addRoute(HttpMethod.GET, path, adapter.adapt(handler));
    }

    public <O> Router post(String path, ContextHandler<O> handler) {
        return addRoute(HttpMethod.POST, path, adapter.adapt(handler));
    }

    public <O> Router put(String path, ContextHandler<O> handler) {
        return addRoute(HttpMethod.PUT, path, adapter.adapt(handler));
    }

    public <O> Router delete(String path, ContextHandler<O> handler) {
        return addRoute(HttpMethod.DELETE, path, adapter.adapt(handler));
    }

    public <O> Router patch(String path, ContextHandler<O> handler) {
        return addRoute(HttpMethod.PATCH, path, adapter.adapt(handler));
    }

    public <O> Router head(String path, ContextHandler<O> handler) {
        return addRoute(HttpMethod.HEAD, path, adapter.adapt(handler));
    }

    public <O> Router options(String path, ContextHandler<O> handler) {
        return addRoute(HttpMethod.OPTIONS, path, adapter.adapt(handler));
    }

    // ---- Typed body handlers (auto-deserialization of R) ----

    public <R, O> Router get(String path, Class<R> bodyType, FountainHandler<R, O> handler) {
        return addRoute(HttpMethod.GET, path, adapter.adapt(bodyType, handler));
    }

    public <R, O> Router post(String path, Class<R> bodyType, FountainHandler<R, O> handler) {
        return addRoute(HttpMethod.POST, path, adapter.adapt(bodyType, handler));
    }

    public <R, O> Router put(String path, Class<R> bodyType, FountainHandler<R, O> handler) {
        return addRoute(HttpMethod.PUT, path, adapter.adapt(bodyType, handler));
    }

    public <R, O> Router delete(String path, Class<R> bodyType, FountainHandler<R, O> handler) {
        return addRoute(HttpMethod.DELETE, path, adapter.adapt(bodyType, handler));
    }

    public <R, O> Router patch(String path, Class<R> bodyType, FountainHandler<R, O> handler) {
        return addRoute(HttpMethod.PATCH, path, adapter.adapt(bodyType, handler));
    }

    // ---- Route groups ----

    public Router group(String groupPrefix, Consumer<Router> configure) {
        Router sub = new Router(adapter, this.prefix + normalizePath(groupPrefix));
        configure.accept(sub);
        for (var entry : sub.routeTable.entrySet()) {
            routeTable.computeIfAbsent(entry.getKey(), _ -> new ArrayList<>())
                    .addAll(entry.getValue());
        }
        return this;
    }

    // ---- Internal ----

    private Router addRoute(HttpMethod method, String path, RouteHandler handler) {
        String fullPath = prefix + normalizePath(path);
        RouteEntry entry = new RouteEntry(method, fullPath, handler);
        routeTable.computeIfAbsent(method, _ -> new ArrayList<>()).add(entry);
        log.info("Registered route: {} {}", method, fullPath);
        return this;
    }

    /**
     * Match a request and dispatch to the handler. Returns null if no route matches.
     */
    public HttpResponse handle(FountainPoolRequest request) throws Exception {
        List<RouteEntry> routes = routeTable.get(request.method());
        if (routes == null) {
            return null;
        }

        String path = request.path();
        String[] requestSegments = splitPath(path);

        for (RouteEntry route : routes) {
            Map<String, String> params = tryMatch(route, requestSegments);
            if (params != null) {
                FountainContext ctx = new FountainContext(request, params);
                return route.handler().handle(ctx);
            }
        }
        return null;
    }

    private Map<String, String> tryMatch(RouteEntry route, String[] requestSegments) {
        String[] routeSegments = route.segments();
        boolean[] isParam = route.isParam();
        String[] paramNames = route.paramNames();

        if (route.hasWildcard()) {
            if (requestSegments.length < routeSegments.length) {
                return null;
            }
        } else {
            if (requestSegments.length != routeSegments.length) {
                return null;
            }
        }

        Map<String, String> params = null;
        for (int i = 0; i < routeSegments.length; i++) {
            if (isParam[i]) {
                if (params == null) {
                    params = new LinkedHashMap<>(4);
                }
                params.put(paramNames[i], requestSegments[i]);
            } else {
                if (!routeSegments[i].equals(requestSegments[i])) {
                    return null;
                }
            }
        }

        return params != null ? params : Collections.emptyMap();
    }

    private static String[] splitPath(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return new String[0];
        }
        String p = path.startsWith("/") ? path.substring(1) : path;
        if (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p.split("/");
    }

    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    public int routeCount() {
        return routeTable.values().stream().mapToInt(List::size).sum();
    }
}
