package com.fountainframework.core.router;

import com.fountainframework.core.handler.FountainContext;
import com.fountainframework.core.handler.FountainHandler;
import com.fountainframework.core.http.FountainPoolRequest;
import com.fountainframework.core.http.HttpMethod;
import com.fountainframework.core.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

/**
 * High-performance HTTP router with Gin-style path parameters.
 * <p>
 * Zero reflection — routes map directly to lambda/method-reference handlers.
 * Path matching uses pre-compiled segment arrays for O(segments) matching.
 * <p>
 * Supports:
 * <ul>
 *   <li>Static paths: {@code /users}</li>
 *   <li>Named parameters: {@code /users/:id}</li>
 *   <li>Wildcard: {@code /static/*}</li>
 *   <li>Route groups: {@code router.group("/api/v1", g -> { ... })}</li>
 * </ul>
 *
 * <pre>{@code
 * Router router = new Router();
 * router.get("/hello", ctx -> HttpResponse.ok("Hello!"));
 * router.get("/users/:id", ctx -> {
 *     String id = ctx.pathParam("id");
 *     return HttpResponse.ok("User: " + id);
 * });
 * }</pre>
 */
public class Router {

    private static final Logger log = LoggerFactory.getLogger(Router.class);

    // Routes indexed by HTTP method for fast lookup
    private final Map<HttpMethod, List<RouteEntry>> routeTable = new EnumMap<>(HttpMethod.class);
    private final String prefix;

    public Router() {
        this("");
    }

    private Router(String prefix) {
        this.prefix = prefix;
    }

    // ---- Route registration ----

    public Router get(String path, FountainHandler handler) {
        return addRoute(HttpMethod.GET, path, handler);
    }

    public Router post(String path, FountainHandler handler) {
        return addRoute(HttpMethod.POST, path, handler);
    }

    public Router put(String path, FountainHandler handler) {
        return addRoute(HttpMethod.PUT, path, handler);
    }

    public Router delete(String path, FountainHandler handler) {
        return addRoute(HttpMethod.DELETE, path, handler);
    }

    public Router patch(String path, FountainHandler handler) {
        return addRoute(HttpMethod.PATCH, path, handler);
    }

    public Router head(String path, FountainHandler handler) {
        return addRoute(HttpMethod.HEAD, path, handler);
    }

    public Router options(String path, FountainHandler handler) {
        return addRoute(HttpMethod.OPTIONS, path, handler);
    }

    /**
     * Register a handler for all HTTP methods.
     */
    public Router any(String path, FountainHandler handler) {
        for (HttpMethod m : HttpMethod.values()) {
            addRoute(m, path, handler);
        }
        return this;
    }

    /**
     * Create a route group with a common prefix.
     * <pre>{@code
     * router.group("/api/v1", api -> {
     *     api.get("/users", ctx -> ...);    // matches /api/v1/users
     *     api.post("/users", ctx -> ...);   // matches /api/v1/users
     * });
     * }</pre>
     */
    public Router group(String groupPrefix, Consumer<Router> configure) {
        Router sub = new Router(this.prefix + normalizePath(groupPrefix));
        configure.accept(sub);
        // Merge sub-router routes into this router
        for (var entry : sub.routeTable.entrySet()) {
            routeTable.computeIfAbsent(entry.getKey(), _ -> new ArrayList<>())
                    .addAll(entry.getValue());
        }
        return this;
    }

    private Router addRoute(HttpMethod method, String path, FountainHandler handler) {
        String fullPath = prefix + normalizePath(path);
        RouteEntry entry = new RouteEntry(method, fullPath, handler);
        routeTable.computeIfAbsent(method, _ -> new ArrayList<>()).add(entry);
        log.info("Registered route: {} {}", method, fullPath);
        return this;
    }

    // ---- Route matching ----

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

    /**
     * Try to match request segments against a route.
     * Returns path parameters map on match, null on mismatch.
     */
    private Map<String, String> tryMatch(RouteEntry route, String[] requestSegments) {
        String[] routeSegments = route.segments();
        boolean[] isParam = route.isParam();
        String[] paramNames = route.paramNames();

        // Wildcard route: request must have at least as many segments as route
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

    // ---- Utilities ----

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
