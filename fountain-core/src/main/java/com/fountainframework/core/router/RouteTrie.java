package com.fountainframework.core.router;

import com.fountainframework.core.handler.RouteHandler;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Segment-level trie for HTTP route matching.
 * <p>
 * Each level corresponds to one "/" segment of the URL path.
 * Static segments use a {@link HashMap} for O(1) child lookup.
 * A single {@code paramChild} handles {@code :name} segments,
 * and {@code wildcardHandler} handles trailing {@code *} patterns.
 * <p>
 * Priority: static segment > param segment > wildcard (same as Gin).
 * <p>
 * Lookup cost is O(depth) where depth = number of path segments (typically 3-5),
 * independent of the total number of registered routes.
 * <p>
 * <b>Route cache:</b> Paths that resolve to a handler with no path parameters
 * are cached in a bounded {@link ConcurrentHashMap}. Subsequent requests to the
 * same static path skip the trie walk entirely. Parameterized matches are never
 * cached because the extracted values differ per request.
 */
final class RouteTrie {

    private static final int MAX_CACHE_SIZE = 1024;

    private final TrieNode root = new TrieNode();
    private int routeCount;

    /**
     * Bounded LRU-ish cache: static path → MatchResult.
     * Only populated for matches that have zero path parameters.
     * Eviction is coarse — when the cache exceeds the limit, it is cleared
     * (hot paths re-populate quickly). This avoids the overhead of a true
     * LRU linked list on every access while keeping memory bounded.
     */
    private final ConcurrentHashMap<String, MatchResult> staticCache = new ConcurrentHashMap<>();

    void addRoute(String[] segments, boolean[] isParam, String[] paramNames,
                  boolean hasWildcard, RouteHandler handler) {
        TrieNode current = root;
        for (int i = 0; i < segments.length; i++) {
            if (isParam[i]) {
                if (current.paramChild == null) {
                    current.paramChild = new TrieNode();
                }
                current.paramChild.paramName = paramNames[i];
                current = current.paramChild;
            } else {
                current = current.staticChildren.computeIfAbsent(segments[i], _ -> new TrieNode());
            }
        }
        if (hasWildcard) {
            current.wildcardHandler = handler;
        } else {
            current.handler = handler;
        }
        routeCount++;
        // Invalidate cache when routes change (registration is rare)
        staticCache.clear();
    }

    MatchResult match(String[] requestSegments) {
        return match(requestSegments, null);
    }

    /**
     * Match request segments against the trie.
     *
     * @param requestSegments the path segments
     * @param rawPath         the original path string — when non-null, enables cache
     *                        lookup/population for static (no-param) matches
     */
    MatchResult match(String[] requestSegments, String rawPath) {
        // Fast path: check the static cache first
        if (rawPath != null) {
            MatchResult cached = staticCache.get(rawPath);
            if (cached != null) {
                return cached;
            }
        }

        Map<String, String> params = null;
        TrieNode current = root;

        for (int i = 0; i < requestSegments.length; i++) {
            String seg = requestSegments[i];

            // Priority 1: exact static match (O(1) HashMap lookup)
            TrieNode staticChild = current.staticChildren.get(seg);
            if (staticChild != null) {
                current = staticChild;
                continue;
            }

            // Priority 2: param child
            if (current.paramChild != null) {
                if (params == null) {
                    params = new LinkedHashMap<>(4);
                }
                params.put(current.paramChild.paramName, seg);
                current = current.paramChild;
                continue;
            }

            // Priority 3: wildcard matches this and all remaining segments
            if (current.wildcardHandler != null) {
                return new MatchResult(current.wildcardHandler,
                        params != null ? params : Collections.emptyMap());
            }

            return null;
        }

        // Reached end of request segments
        RouteHandler handler = current.handler;
        if (handler == null) {
            handler = current.wildcardHandler;
        }
        if (handler == null) {
            return null;
        }

        Map<String, String> resultParams = params != null ? params : Collections.emptyMap();
        MatchResult result = new MatchResult(handler, resultParams);

        // Cache only static matches (no path parameters extracted)
        if (rawPath != null && resultParams.isEmpty()) {
            if (staticCache.size() >= MAX_CACHE_SIZE) {
                staticCache.clear();
            }
            staticCache.put(rawPath, result);
        }

        return result;
    }

    int routeCount() {
        return routeCount;
    }

    static final class TrieNode {
        final Map<String, TrieNode> staticChildren = new HashMap<>();
        TrieNode paramChild;
        String paramName;
        RouteHandler handler;
        RouteHandler wildcardHandler;
    }

    record MatchResult(RouteHandler handler, Map<String, String> params) {}
}
