package com.fountainframework.core.server;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Auto-detects the best available Netty transport for the current platform.
 * <p>
 * Resolution order:
 * <ol>
 *   <li><b>Epoll</b> — Linux only, requires {@code netty-transport-native-epoll} on the classpath</li>
 *   <li><b>KQueue</b> — macOS/BSD only, requires {@code netty-transport-native-kqueue} on the classpath</li>
 *   <li><b>NIO</b> — universal Java fallback, always available</li>
 * </ol>
 * <p>
 * Native transports bypass the JDK selector layer and use platform-specific syscalls
 * (epoll_wait / kqueue), reducing context switches and GC pressure from selector key sets.
 */
public final class NativeTransport {

    private static final Logger log = LoggerFactory.getLogger(NativeTransport.class);

    private NativeTransport() {}

    /** Resolved once at startup — immutable after init. */
    private static final TransportType DETECTED = detect();

    private enum TransportType { EPOLL, KQUEUE, NIO }

    private static TransportType detect() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("linux")) {
            try {
                Class.forName("io.netty.channel.epoll.Epoll");
                if (io.netty.channel.epoll.Epoll.isAvailable()) {
                    return TransportType.EPOLL;
                }
                log.debug("Epoll class found but not available: {}",
                        io.netty.channel.epoll.Epoll.unavailabilityCause().getMessage());
            } catch (ClassNotFoundException ignored) {
                // native jar not on classpath
            }
        }
        if (os.contains("mac") || os.contains("darwin") || os.contains("bsd")) {
            try {
                Class.forName("io.netty.channel.kqueue.KQueue");
                if (io.netty.channel.kqueue.KQueue.isAvailable()) {
                    return TransportType.KQUEUE;
                }
                log.debug("KQueue class found but not available: {}",
                        io.netty.channel.kqueue.KQueue.unavailabilityCause().getMessage());
            } catch (ClassNotFoundException ignored) {
                // native jar not on classpath
            }
        }
        return TransportType.NIO;
    }

    /**
     * Creates an {@link EventLoopGroup} using the best available transport.
     *
     * @param nThreads number of threads (0 = Netty default)
     */
    public static EventLoopGroup newEventLoopGroup(int nThreads) {
        return switch (DETECTED) {
            case EPOLL  -> new io.netty.channel.epoll.EpollEventLoopGroup(nThreads);
            case KQUEUE -> new io.netty.channel.kqueue.KQueueEventLoopGroup(nThreads);
            case NIO    -> new NioEventLoopGroup(nThreads);
        };
    }

    /**
     * Returns the {@link ServerChannel} class matching the detected transport.
     */
    public static Class<? extends ServerChannel> serverChannelClass() {
        return switch (DETECTED) {
            case EPOLL  -> io.netty.channel.epoll.EpollServerSocketChannel.class;
            case KQUEUE -> io.netty.channel.kqueue.KQueueServerSocketChannel.class;
            case NIO    -> NioServerSocketChannel.class;
        };
    }

    /**
     * Returns a human-readable name for the detected transport (for logging).
     */
    public static String name() {
        return DETECTED.name().toLowerCase();
    }
}
