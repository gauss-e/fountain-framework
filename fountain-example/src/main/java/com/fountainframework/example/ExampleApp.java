package com.fountainframework.example;

import com.fountainframework.core.Fountain;

/**
 * Example application demonstrating Fountain's Spring Boot-like startup.
 * <p>
 * {@code Fountain.run(ExampleApp.class)} scans this package for
 * {@code @FountainRouter} classes and auto-registers their routes.
 *
 * @see UserRoutes
 */
public class ExampleApp {

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        Fountain.run(ExampleApp.class).start(port);
    }
}
