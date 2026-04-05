package com.fountainframework.core.routing;

import com.fountainframework.core.annotation.*;
import com.fountainframework.core.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registry that stores route mappings and matches incoming requests to handlers.
 */
public class RouteRegistry {

    private static final Logger log = LoggerFactory.getLogger(RouteRegistry.class);
    private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("\\{(\\w+)}");

    private final List<RouteEntry> routes = new ArrayList<>();

    /**
     * Scan a handler object for annotated methods and register them as routes.
     */
    public void register(Object handler) {
        Class<?> clazz = handler.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            tryRegister(handler, method, method.getAnnotation(Get.class), HttpMethod.GET, a -> ((Get) a).value());
            tryRegister(handler, method, method.getAnnotation(Post.class), HttpMethod.POST, a -> ((Post) a).value());
            tryRegister(handler, method, method.getAnnotation(Put.class), HttpMethod.PUT, a -> ((Put) a).value());
            tryRegister(handler, method, method.getAnnotation(Delete.class), HttpMethod.DELETE, a -> ((Delete) a).value());
            tryRegister(handler, method, method.getAnnotation(Patch.class), HttpMethod.PATCH, a -> ((Patch) a).value());
        }
    }

    private void tryRegister(Object handler, Method method, Annotation annotation,
                             HttpMethod httpMethod, PathExtractor extractor) {
        if (annotation == null) {
            return;
        }
        String path = extractor.extract(annotation);
        List<String> variableNames = new ArrayList<>();
        Matcher matcher = PATH_VARIABLE_PATTERN.matcher(path);
        StringBuilder regex = new StringBuilder("^");
        int last = 0;
        while (matcher.find()) {
            regex.append(Pattern.quote(path.substring(last, matcher.start())));
            regex.append("([^/]+)");
            variableNames.add(matcher.group(1));
            last = matcher.end();
        }
        regex.append(Pattern.quote(path.substring(last)));
        regex.append("$");

        method.setAccessible(true);
        RouteEntry entry = new RouteEntry(httpMethod, path, Pattern.compile(regex.toString()),
                variableNames, method, handler);
        routes.add(entry);
        log.info("Registered route: {}", entry);
    }

    /**
     * Find a matching route for the given HTTP method and path.
     */
    public MatchResult match(HttpMethod method, String path) {
        for (RouteEntry entry : routes) {
            if (entry.httpMethod() != method) {
                continue;
            }
            Matcher matcher = entry.compiledPattern().matcher(path);
            if (matcher.matches()) {
                Map<String, String> pathVariables = new LinkedHashMap<>();
                List<String> names = entry.pathVariableNames();
                for (int i = 0; i < names.size(); i++) {
                    pathVariables.put(names.get(i), matcher.group(i + 1));
                }
                return new MatchResult(entry, pathVariables);
            }
        }
        return null;
    }

    public List<RouteEntry> allRoutes() {
        return Collections.unmodifiableList(routes);
    }

    /**
     * Result of a successful route match, including extracted path variables.
     */
    public record MatchResult(RouteEntry route, Map<String, String> pathVariables) {}

    @FunctionalInterface
    private interface PathExtractor {
        String extract(Annotation annotation);
    }
}
