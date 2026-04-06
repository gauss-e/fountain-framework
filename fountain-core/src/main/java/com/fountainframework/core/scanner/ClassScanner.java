package com.fountainframework.core.scanner;

import com.fountainframework.core.router.FountainRouter;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

/**
 * ASM bytecode-based classpath scanner.
 * <p>
 * Scans .class files using ASM {@link ClassReader} without loading classes,
 * similar to Spring's classpath scanning approach.
 * <p>
 * Supports:
 * <ul>
 *   <li>Inner classes ({@code Outer$Inner.class})</li>
 *   <li>Meta-annotations (e.g. user defines {@code @MyRouter} annotated with {@code @FountainRouter})</li>
 * </ul>
 */
public final class ClassScanner {

    private static final Logger log = LoggerFactory.getLogger(ClassScanner.class);

    private static final String FOUNTAIN_ROUTER_DESC =
            "L" + FountainRouter.class.getName().replace('.', '/') + ";";

    // Cache: annotation descriptor -> whether it is (meta-)annotated with @FountainRouter
    private final Map<String, Boolean> metaAnnotationCache = new HashMap<>();

    private final ClassLoader classLoader;

    public ClassScanner() {
        this(Thread.currentThread().getContextClassLoader());
    }

    public ClassScanner(ClassLoader classLoader) {
        this.classLoader = classLoader;
        // @FountainRouter itself is always a match
        metaAnnotationCache.put(FOUNTAIN_ROUTER_DESC, true);
    }

    /**
     * Scan the given package (and sub-packages) for classes annotated with
     * {@link FountainRouter} (directly or via meta-annotation).
     * Returns the fully qualified class names.
     */
    public List<String> scan(String basePackage) {
        String basePath = basePackage.replace('.', '/');
        List<String> result = new ArrayList<>();

        try {
            Enumeration<URL> resources = classLoader.getResources(basePath);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                if ("file".equals(resource.getProtocol())) {
                    File directory = new File(resource.toURI());
                    scanDirectory(directory, basePackage, result);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan package: " + basePackage, e);
        }

        return result;
    }

    private void scanDirectory(File directory, String packageName, List<String> result) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), result);
            } else if (file.getName().endsWith(".class")) {
                // Includes inner classes: Foo$Bar.class -> com.example.Foo$Bar
                String className = packageName + "." + file.getName().replace(".class", "");
                if (hasRouterAnnotation(className)) {
                    result.add(className);
                }
            }
        }
    }

    /**
     * Check if a class has @FountainRouter (directly or via meta-annotation)
     * by reading its bytecode with ASM — no Class.forName.
     */
    private boolean hasRouterAnnotation(String className) {
        String resourcePath = className.replace('.', '/') + ".class";
        try (InputStream is = classLoader.getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.debug("Could not find .class resource: {}", resourcePath);
                return false;
            }
            ClassReader reader = createClassReader(is);
            RouterAnnotationVisitor visitor = new RouterAnnotationVisitor();
            // SKIP_CODE | SKIP_DEBUG | SKIP_FRAMES — we only need annotation metadata
            reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return visitor.isRouter;
        } catch (IOException e) {
            log.debug("Could not read class bytecode: {}", className, e);
            return false;
        }
    }

    /**
     * Check if an annotation (by its descriptor) is @FountainRouter or is
     * itself meta-annotated with @FountainRouter.
     */
    private boolean isRouterAnnotation(String annotationDesc) {
        Boolean cached = metaAnnotationCache.get(annotationDesc);
        if (cached != null) {
            return cached;
        }

        // Prevent infinite recursion for cyclic annotations
        metaAnnotationCache.put(annotationDesc, false);

        // Convert descriptor "Lcom/example/MyRouter;" -> "com/example/MyRouter.class"
        String internalName = annotationDesc.substring(1, annotationDesc.length() - 1);
        String resourcePath = internalName + ".class";

        try (InputStream is = classLoader.getResourceAsStream(resourcePath)) {
            if (is == null) {
                return false;
            }
            ClassReader reader = createClassReader(is);
            MetaAnnotationVisitor visitor = new MetaAnnotationVisitor();
            reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            boolean result = visitor.hasRouterMeta;
            metaAnnotationCache.put(annotationDesc, result);
            return result;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Create a ClassReader from an InputStream, downgrading the class file major version
     * if it exceeds what this ASM version supports. Safe because we only read annotation
     * metadata, which has not changed across recent class file versions.
     */
    private static ClassReader createClassReader(InputStream is) throws IOException {
        byte[] bytes = is.readAllBytes();
        // Class file major version is at bytes[6..7]. ASM 9.x supports up to version 67 (Java 23).
        // If the class was compiled with a newer JDK, downgrade so ASM can parse it.
        int majorVersion = ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
        if (majorVersion > 67) {
            bytes[6] = (byte) (67 >>> 8);
            bytes[7] = (byte) 67;
        }
        return new ClassReader(bytes);
    }

    /**
     * Visits a class's annotations to determine if it has @FountainRouter
     * (directly or via meta-annotation). Skips annotations and interfaces —
     * only concrete/abstract classes are returned as router candidates.
     */
    private class RouterAnnotationVisitor extends ClassVisitor {
        boolean isRouter = false;
        boolean isConcreteClass = true;

        RouterAnnotationVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            // Skip annotations, interfaces, and abstract classes
            if ((access & (Opcodes.ACC_ANNOTATION | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT)) != 0) {
                isConcreteClass = false;
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (visible && isConcreteClass && isRouterAnnotation(descriptor)) {
                isRouter = true;
            }
            return null;
        }
    }

    /**
     * Visits an annotation class's annotations to check for @FountainRouter meta-annotation.
     */
    private class MetaAnnotationVisitor extends ClassVisitor {
        boolean hasRouterMeta = false;

        MetaAnnotationVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (visible && isRouterAnnotation(descriptor)) {
                hasRouterMeta = true;
            }
            return null;
        }
    }
}
