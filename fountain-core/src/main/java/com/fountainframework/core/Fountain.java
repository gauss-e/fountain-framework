package com.fountainframework.core;

import com.fountainframework.core.router.FountainRouter;
import com.fountainframework.core.router.Router;
import com.fountainframework.core.router.RouterConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Bootstrap entry point for Fountain applications.
 * Scans the package of the given class for {@link FountainRouter} annotated
 * {@link RouterConfigurer} implementations, assembles routes, and returns
 * a configured {@link FountainApplication} ready to start.
 *
 * <pre>{@code
 * public class MyApp {
 *     public static void main(String[] args) {
 *         Fountain.run(MyApp.class).start(8080);
 *     }
 * }
 * }</pre>
 */
public final class Fountain {

    private static final Logger log = LoggerFactory.getLogger(Fountain.class);

    private Fountain() {}

    /**
     * Scan the package (and sub-packages) of {@code appClass} for
     * {@link FountainRouter}-annotated {@link RouterConfigurer} classes,
     * instantiate them, configure routes, and return a ready-to-start application.
     */
    public static FountainApplication run(Class<?> appClass) {
        String basePackage = appClass.getPackageName();
        log.info("Fountain starting — scanning package: {}", basePackage);

        List<Class<?>> classes = scanPackage(basePackage);
        FountainApplication app = FountainApplication.create();
        Router router = app.router();

        int configurerCount = 0;
        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(FountainRouter.class)) {
                if (!RouterConfigurer.class.isAssignableFrom(clazz)) {
                    log.warn("@FountainRouter class {} does not implement RouterConfigurer, skipping", clazz.getName());
                    continue;
                }
                try {
                    RouterConfigurer configurer = (RouterConfigurer) clazz.getDeclaredConstructor().newInstance();
                    configurer.configure(router);
                    configurerCount++;
                    log.info("Loaded router configurer: {}", clazz.getSimpleName());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to instantiate router configurer: " + clazz.getName(), e);
                }
            }
        }

        log.info("Fountain assembled — {} router configurer(s) loaded, {} route(s) registered",
                configurerCount, router.routeCount());
        return app;
    }

    /**
     * Scan all classes in the given package and sub-packages.
     */
    private static List<Class<?>> scanPackage(String basePackage) {
        String path = basePackage.replace('.', '/');
        List<Class<?>> classes = new ArrayList<>();

        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = classLoader.getResources(path);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                if ("file".equals(resource.getProtocol())) {
                    File directory = new File(resource.toURI());
                    scanDirectory(directory, basePackage, classes);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan package: " + basePackage, e);
        }

        return classes;
    }

    private static void scanDirectory(File directory, String packageName, List<Class<?>> classes) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), classes);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                try {
                    classes.add(Class.forName(className));
                } catch (ClassNotFoundException e) {
                    log.debug("Could not load class: {}", className);
                }
            }
        }
    }
}
