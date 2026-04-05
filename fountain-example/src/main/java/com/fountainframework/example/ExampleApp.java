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
 * <p>
 * Port and virtual thread pool size are configured via {@code config.properties}
 * or {@code application.properties} in the classpath. Defaults: port=8080, virtualthread.num=1000.
 *
 * @see UserRoutes        — direct @FountainRouter
 * @see StatusRoutes      — via custom meta-annotation @ApiRouter
 * @see HealthRoutes      — inner class with @FountainRouter
 */
public class ExampleApp {

    public static void main(String[] args) {
        Fountain.run(ExampleApp.class, args).start();
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
