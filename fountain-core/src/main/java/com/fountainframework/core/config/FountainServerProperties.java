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

    // ---- Network tuning ----

    /** TCP listen backlog queue depth. */
    public static final String SERVER_SO_BACKLOG = "fountain.server.so-backlog";
    public static final int SERVER_SO_BACKLOG_DEFAULT = 1024;

    /** Disable Nagle's algorithm for low-latency request/response. */
    public static final String SERVER_TCP_NODELAY = "fountain.server.tcp-nodelay";
    public static final boolean SERVER_TCP_NODELAY_DEFAULT = true;

    /** Allow rapid server restart on the same port. */
    public static final String SERVER_SO_REUSEADDR = "fountain.server.so-reuseaddr";
    public static final boolean SERVER_SO_REUSEADDR_DEFAULT = true;

    /** Keep-alive probes for idle connections. */
    public static final String SERVER_SO_KEEPALIVE = "fountain.server.so-keepalive";
    public static final boolean SERVER_SO_KEEPALIVE_DEFAULT = true;

    /** Low watermark (bytes) for the channel write buffer — resumes writing when buffer drops below this. */
    public static final String SERVER_WRITE_BUFFER_LOW = "fountain.server.write-buffer-low";
    public static final int SERVER_WRITE_BUFFER_LOW_DEFAULT = 32 * 1024;      // 32 KB

    /** High watermark (bytes) for the channel write buffer — pauses writing when buffer exceeds this. */
    public static final String SERVER_WRITE_BUFFER_HIGH = "fountain.server.write-buffer-high";
    public static final int SERVER_WRITE_BUFFER_HIGH_DEFAULT = 64 * 1024;     // 64 KB
}
