package com.fountainframework.core.router;

import com.fountainframework.core.handler.RouteHandler;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

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
 */
final class RouteTrie {

    private final TrieNode root = new TrieNode();
    private int routeCount;

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
    }

    MatchResult match(String[] requestSegments) {
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
        if (current.handler != null) {
            return new MatchResult(current.handler,
                    params != null ? params : Collections.emptyMap());
        }
        if (current.wildcardHandler != null) {
            return new MatchResult(current.wildcardHandler,
                    params != null ? params : Collections.emptyMap());
        }
        return null;
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
