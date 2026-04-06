package com.fountainframework.example;

import com.fountainframework.core.Fountain;
import com.fountainframework.core.http.HttpResponse;
import com.fountainframework.core.router.FountainRouter;
import com.fountainframework.core.router.Router;
import com.fountainframework.core.router.RouterConfigurer;

/**
 * Example application demonstrating Fountain's Spring Boot-like startup.
 * <p>
 * {@code Fountain.run(ExampleApp.class)} scans this package for
 * {@code @FountainRouter} classes (including inner classes and meta-annotations)
 * and auto-registers their routes.
 *
 * @see UserRoutes        — direct @FountainRouter
 * @see StatusRoutes      — via custom meta-annotation @ApiRouter
 * @see HealthRoutes      — inner class with @FountainRouter
 */
public class ExampleApp {

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        Fountain.run(ExampleApp.class).start(port);
    }

    /**
     * Inner class router — demonstrates that inner classes annotated with
     * @FountainRouter are also discovered by the ASM bytecode scanner.
     */
    @FountainRouter
    public static class HealthRoutes implements RouterConfigurer {
        @Override
        public void configure(Router router) {
            router.get("/health", ctx ->
                    HttpResponse.json("{\"healthy\":true}"));
        }
    }
}
