package com.fountainframework.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration loader for Fountain applications.
 * <p>
 * Loads configuration from the classpath in priority order:
 * <ol>
 *   <li>{@code config.properties}</li>
 *   <li>{@code config.yaml} / {@code config.yml}</li>
 *   <li>{@code application.properties}</li>
 *   <li>{@code application.yaml} / {@code application.yml}</li>
 * </ol>
 * The first file found wins — no merging across files.
 * If no file is found, all values fall back to their defaults.
 *
 * <p>Supported keys:
 * <ul>
 *   <li>{@code fountain.server.port} — server listen port (default: 8080)</li>
 *   <li>{@code fountain.server.virtualthread.num} — virtual thread pool size (default: 1000)</li>
 * </ul>
 */
public final class FountainConfig {

    private static final Logger log = LoggerFactory.getLogger(FountainConfig.class);

    public static final int DEFAULT_PORT = 8080;
    public static final int DEFAULT_VIRTUAL_THREAD_NUM = 1000;

    private static final String KEY_PORT = "fountain.server.port";
    private static final String KEY_VIRTUAL_THREAD_NUM = "fountain.server.virtualthread.num";

    private static final String[] CONFIG_FILES = {
            "config.properties",
            "config.yaml",
            "config.yml",
            "application.properties",
            "application.yaml",
            "application.yml"
    };

    private final Properties properties;

    private FountainConfig(Properties properties) {
        this.properties = properties;
    }

    /**
     * Load configuration from the classpath using the fallback chain.
     */
    public static FountainConfig load() {
        return load(Thread.currentThread().getContextClassLoader());
    }

    /**
     * Load configuration from the classpath using the given class loader.
     */
    public static FountainConfig load(ClassLoader classLoader) {
        for (String file : CONFIG_FILES) {
            try (InputStream is = classLoader.getResourceAsStream(file)) {
                if (is == null) {
                    continue;
                }
                Properties props;
                if (file.endsWith(".yaml") || file.endsWith(".yml")) {
                    props = loadYaml(is);
                } else {
                    props = loadProperties(is);
                }
                log.info("Loaded configuration from: {}", file);
                return new FountainConfig(props);
            } catch (IOException e) {
                log.warn("Failed to read config file: {}", file, e);
            }
        }
        log.info("No configuration file found, using defaults");
        return new FountainConfig(new Properties());
    }

    private static Properties loadProperties(InputStream is) throws IOException {
        Properties props = new Properties();
        props.load(is);
        return props;
    }

    /**
     * Flatten a nested YAML map into dot-separated keys.
     * <pre>
     * fountain:
     *   server:
     *     port: 9090
     * </pre>
     * becomes {@code fountain.server.port=9090}
     */
    private static Properties loadYaml(InputStream is) {
        Yaml yaml = new Yaml();
        Map<String, Object> map = yaml.load(is);
        Properties props = new Properties();
        if (map != null) {
            flattenMap("", map, props);
        }
        return props;
    }

    @SuppressWarnings("unchecked")
    private static void flattenMap(String prefix, Map<String, Object> map, Properties props) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flattenMap(key, (Map<String, Object>) value, props);
            } else if (value != null) {
                props.setProperty(key, value.toString());
            }
        }
    }

    // ---- Typed accessors ----

    public String getString(String key) {
        return properties.getProperty(key);
    }

    public String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for key '{}': '{}', using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    // ---- Convenience methods for known keys ----

    public int getPort() {
        return getInt(KEY_PORT, DEFAULT_PORT);
    }

    public int getVirtualThreadNum() {
        return getInt(KEY_VIRTUAL_THREAD_NUM, DEFAULT_VIRTUAL_THREAD_NUM);
    }
}
