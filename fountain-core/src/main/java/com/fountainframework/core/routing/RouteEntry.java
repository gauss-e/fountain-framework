package com.fountainframework.core.routing;

import com.fountainframework.core.http.HttpMethod;

import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A single route entry mapping an HTTP method + path pattern to a handler method.
 */
public class RouteEntry {

    private final HttpMethod httpMethod;
    private final String pathPattern;
    private final Pattern compiledPattern;
    private final List<String> pathVariableNames;
    private final Method handlerMethod;
    private final Object handlerInstance;

    public RouteEntry(HttpMethod httpMethod, String pathPattern, Pattern compiledPattern,
                      List<String> pathVariableNames, Method handlerMethod, Object handlerInstance) {
        this.httpMethod = httpMethod;
        this.pathPattern = pathPattern;
        this.compiledPattern = compiledPattern;
        this.pathVariableNames = pathVariableNames;
        this.handlerMethod = handlerMethod;
        this.handlerInstance = handlerInstance;
    }

    public HttpMethod httpMethod() {
        return httpMethod;
    }

    public String pathPattern() {
        return pathPattern;
    }

    public Pattern compiledPattern() {
        return compiledPattern;
    }

    public List<String> pathVariableNames() {
        return pathVariableNames;
    }

    public Method handlerMethod() {
        return handlerMethod;
    }

    public Object handlerInstance() {
        return handlerInstance;
    }

    @Override
    public String toString() {
        return httpMethod + " " + pathPattern + " -> " + handlerMethod.getDeclaringClass().getSimpleName() + "." + handlerMethod.getName() + "()";
    }
}
