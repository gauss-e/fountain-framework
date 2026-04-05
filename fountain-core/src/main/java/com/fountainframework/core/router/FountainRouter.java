package com.fountainframework.core.router;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a Fountain router configuration.
 * The annotated class must implement {@link RouterConfigurer}.
 * <p>
 * Fountain will auto-discover classes with this annotation at startup
 * and invoke {@link RouterConfigurer#configure(Router)} to register routes.
 *
 * <pre>{@code
 * @FountainRouter
 * public class ApiRoutes implements RouterConfigurer {
 *     @Override
 *     public void configure(Router router) {
 *         router.get("/hello", ctx -> HttpResponse.ok("Hello!"));
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FountainRouter {
}
