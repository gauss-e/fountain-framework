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
 * Configuration is resolved in the following priority order (highest wins):
 * <ol>
 *   <li><b>Command-line arguments</b> — {@code --key=value} format</li>
 *   <li><b>Config file</b> from the classpath (first found wins, no merging):
 *       {@code config.properties}, {@code config.yaml/yml},
 *       {@code application.properties}, {@code application.yaml/yml}</li>
 *   <li><b>Built-in defaults</b> defined in {@link ConfigKeys}</li>
 * </ol>
 *
 * @see ConfigKeys for all supported keys and their defaults
 */
public final class FountainConfig {

    private static final Logger log = LoggerFactory.getLogger(FountainConfig.class);

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
     * Load configuration from the classpath (no args override).
     */
    public static FountainConfig load() {
        return load(new String[0]);
    }

    /**
     * Load configuration from the classpath, then overlay command-line args.
     * <p>
     * Args are parsed as {@code --key=value} pairs. For example:
     * {@code --fountain.server.port=9090 --fountain.server.max-concurrency=2000}
     */
    public static FountainConfig load(String[] args) {
        return load(Thread.currentThread().getContextClassLoader(), args);
    }

    /**
     * Load configuration using the given class loader, then overlay command-line args.
     */
    public static FountainConfig load(ClassLoader classLoader, String[] args) {
        Properties props = loadFromClasspath(classLoader);
        applyArgs(props, args);
        return new FountainConfig(props);
    }

    private static Properties loadFromClasspath(ClassLoader classLoader) {
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
                return props;
            } catch (IOException e) {
                log.warn("Failed to read config file: {}", file, e);
            }
        }
        log.info("No configuration file found, using defaults");
        return new Properties();
    }

    /**
     * Parse {@code --key=value} arguments and put them into the properties,
     * overriding any values loaded from config files.
     */
    private static void applyArgs(Properties props, String[] args) {
        if (args == null) {
            return;
        }
        for (String arg : args) {
            if (arg.startsWith("--") && arg.contains("=")) {
                String kv = arg.substring(2);
                int eq = kv.indexOf('=');
                String key = kv.substring(0, eq);
                String value = kv.substring(eq + 1);
                props.setProperty(key, value);
                log.debug("Arg override: {} = {}", key, value);
            }
        }
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
        return getInt(ConfigKeys.SERVER_PORT, ConfigKeys.SERVER_PORT_DEFAULT);
    }

    public int getMaxConcurrency() {
        return getInt(ConfigKeys.SERVER_MAX_CONCURRENCY, ConfigKeys.SERVER_MAX_CONCURRENCY_DEFAULT);
    }
}
