package com.fountainframework.core.server;

import com.fountainframework.core.annotation.RequestBody;
import com.fountainframework.core.http.FountainPoolRequest;
import com.fountainframework.core.http.HttpMethod;
import com.fountainframework.core.http.HttpResponse;
import com.fountainframework.core.routing.RouteRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;

/**
 * Dispatches incoming HTTP requests to the matching handler method.
 * Handles parameter binding including path variables, request body deserialization,
 * and injection of the FountainPoolRequest object.
 */
public class RequestDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RequestDispatcher.class);
    private final RouteRegistry registry;
    private final ObjectMapper objectMapper;

    public RequestDispatcher(RouteRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    public HttpResponse dispatch(FountainPoolRequest request) {
        HttpMethod method = request.method();
        String path = request.path();

        RouteRegistry.MatchResult match = registry.match(method, path);
        if (match == null) {
            return HttpResponse.notFound();
        }

        try {
            Object result = invokeHandler(match, request);
            return toResponse(result);
        } catch (Exception e) {
            log.error("Error dispatching {} {}", method, path, e);
            return HttpResponse.error("Internal Server Error");
        }
    }

    private Object invokeHandler(RouteRegistry.MatchResult match, FountainPoolRequest request) throws Exception {
        Method handlerMethod = match.route().handlerMethod();
        Object handler = match.route().handlerInstance();
        Parameter[] params = handlerMethod.getParameters();
        Object[] args = new Object[params.length];
        Map<String, String> pathVars = match.pathVariables();
        List<String> pathVarNames = match.route().pathVariableNames();

        int pathVarIndex = 0;
        for (int i = 0; i < params.length; i++) {
            args[i] = resolveParameter(params[i], request, pathVars, pathVarNames, pathVarIndex);
            // Track how many non-special params we've consumed for positional fallback
            if (!FountainPoolRequest.class.isAssignableFrom(params[i].getType())
                    && !params[i].isAnnotationPresent(RequestBody.class)) {
                pathVarIndex++;
            }
        }

        return handlerMethod.invoke(handler, args);
    }

    private Object resolveParameter(Parameter param, FountainPoolRequest request,
                                    Map<String, String> pathVars, List<String> pathVarNames,
                                    int pathVarIndex) throws Exception {
        Class<?> type = param.getType();

        // Inject FountainPoolRequest directly
        if (FountainPoolRequest.class.isAssignableFrom(type)) {
            return request;
        }

        // @RequestBody: deserialize JSON body
        if (param.isAnnotationPresent(RequestBody.class)) {
            return objectMapper.readValue(request.body(), type);
        }

        // Path variable by parameter name
        String name = param.getName();
        if (pathVars.containsKey(name)) {
            return convertPathVariable(pathVars.get(name), type);
        }

        // Positional fallback: if there are path variables and this param index matches
        if (pathVarIndex < pathVarNames.size()) {
            String varName = pathVarNames.get(pathVarIndex);
            String value = pathVars.get(varName);
            if (value != null) {
                return convertPathVariable(value, type);
            }
        }

        // Query parameter by parameter name
        String queryValue = request.queryParameter(name);
        if (queryValue != null) {
            return convertPathVariable(queryValue, type);
        }

        return null;
    }

    private Object convertPathVariable(String value, Class<?> type) {
        if (type == String.class) return value;
        if (type == int.class || type == Integer.class) return Integer.parseInt(value);
        if (type == long.class || type == Long.class) return Long.parseLong(value);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
        if (type == double.class || type == Double.class) return Double.parseDouble(value);
        return value;
    }

    private HttpResponse toResponse(Object result) throws Exception {
        if (result instanceof HttpResponse response) {
            return response;
        }
        if (result instanceof String s) {
            return HttpResponse.ok(s);
        }
        // Serialize to JSON for other return types
        String json = objectMapper.writeValueAsString(result);
        return HttpResponse.json(json);
    }
}
