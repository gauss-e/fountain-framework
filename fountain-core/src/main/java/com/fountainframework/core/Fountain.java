package com.fountainframework.core;

import com.fountainframework.core.config.FountainConfig;
import com.fountainframework.core.router.Router;
import com.fountainframework.core.router.RouterConfigurer;
import com.fountainframework.core.scanner.ClassScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Bootstrap entry point for Fountain applications.
 * <p>
 * Loads configuration from the classpath ({@code config.properties}, {@code config.yaml},
 * or {@code application.properties}, {@code application.yaml}), scans the package of the
 * given class for {@link com.fountainframework.core.router.FountainRouter} annotated
 * {@link RouterConfigurer} implementations using ASM bytecode visiting, assembles routes,
 * and returns a configured {@link FountainApplication} ready to start.
 *
 * <pre>{@code
 * public class MyApp {
 *     public static void main(String[] args) {
 *         Fountain.run(MyApp.class).start();   // port from config or default 8080
 *     }
 * }
 * }</pre>
 */
public final class Fountain {

    private static final Logger log = LoggerFactory.getLogger(Fountain.class);

    private Fountain() {}

    /**
     * Scan the package (and sub-packages) of {@code appClass} for
     * {@code @FountainRouter}-annotated {@link RouterConfigurer} classes
     * using ASM bytecode scanning, instantiate them, configure routes,
     * and return a ready-to-start application.
     * <p>
     * Configuration is loaded automatically from the classpath.
     */
    public static FountainApplication run(Class<?> appClass) {
        FountainConfig config = FountainConfig.load();

        String basePackage = appClass.getPackageName();
        log.info("Fountain starting — scanning package: {}", basePackage);

        ClassScanner scanner = new ClassScanner();
        List<String> classNames = scanner.scan(basePackage);

        FountainApplication app = FountainApplication.create(config);
        Router router = app.router();

        int configurerCount = 0;
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                if (!RouterConfigurer.class.isAssignableFrom(clazz)) {
                    log.warn("@FountainRouter class {} does not implement RouterConfigurer, skipping",
                            clazz.getName());
                    continue;
                }
                RouterConfigurer configurer = (RouterConfigurer) clazz.getDeclaredConstructor().newInstance();
                configurer.configure(router);
                configurerCount++;
                log.info("Loaded router configurer: {}", clazz.getSimpleName());
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate router configurer: " + className, e);
            }
        }

        log.info("Fountain assembled — {} router configurer(s) loaded, {} route(s) registered",
                configurerCount, router.routeCount());
        return app;
    }
}
