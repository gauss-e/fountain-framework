package com.fountainframework.core.router;

/**
 * Interface for defining route configurations.
 * Implement this interface and annotate with {@link FountainRouter} to auto-register routes.
 *
 * <pre>{@code
 * @FountainRouter
 * public class UserRoutes implements RouterConfigurer {
 *     @Override
 *     public void configure(Router router) {
 *         router.get("/users/:id", ctx -> {
 *             long id = ctx.pathParamAsLong("id");
 *             return HttpResponse.ok("User: " + id);
 *         });
 *     }
 * }
 * }</pre>
 */
public interface RouterConfigurer {

    void configure(Router router);
}
