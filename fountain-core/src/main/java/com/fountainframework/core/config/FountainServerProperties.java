package com.fountainframework.core.config;

/**
 * Constants for all supported configuration keys and their default values.
 */
public final class FountainServerProperties {

    private FountainServerProperties() {}

    public static final String SERVER_PORT = "fountain.server.port";
    public static final int SERVER_PORT_DEFAULT = 8080;

    public static final String SERVER_MAX_CONCURRENCY = "fountain.server.max-concurrency";
    public static final int SERVER_MAX_CONCURRENCY_DEFAULT = 1000;
}
