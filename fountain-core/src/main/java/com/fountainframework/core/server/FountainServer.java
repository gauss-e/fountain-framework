package com.fountainframework.core.server;

import com.fountainframework.core.config.FountainConfig;
import com.fountainframework.core.router.Router;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Netty-based HTTP server with virtual-thread-per-task execution.
 * <p>
 * Netty event loops handle I/O(accept, read, write). The best available transport
 * is selected automatically via {@link NativeTransport} — epoll on Linux, kqueue on
 * macOS, NIO as universal fallback.
 * <p>
 * Handler business logic is dispatched to virtual threads — one per request.
 * A {@link Semaphore} caps concurrency to prevent unbounded resource usage under load.
 */
public class FountainServer {

    private static final Logger log = LoggerFactory.getLogger(FountainServer.class);

    private final int port;
    private final Router router;
    private final FountainConfig config;
    private final int maxConcurrency;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ExecutorService virtualThreadPool;
    private Channel serverChannel;

    public FountainServer(int port, Router router, int maxConcurrency, FountainConfig config) {
        this.port = port;
        this.router = router;
        this.maxConcurrency = maxConcurrency;
        this.config = config;
    }

    public void start() throws InterruptedException {
        virtualThreadPool = Executors.newVirtualThreadPerTaskExecutor();
        Semaphore concurrencyLimiter = new Semaphore(maxConcurrency);

        // Auto-detect best transport: epoll (Linux) > kqueue (macOS) > NIO (fallback)
        bossGroup = NativeTransport.newEventLoopGroup(1);
        workerGroup = NativeTransport.newEventLoopGroup(0);

        // Resolve socket options from config (or use sensible defaults)
        int soBacklog       = configOr(FountainConfig::getSoBacklog, 1024);
        boolean tcpNoDelay  = configOr(FountainConfig::getTcpNodelay, true);
        boolean soReuseaddr = configOr(FountainConfig::getSoReuseaddr, true);
        boolean soKeepalive = configOr(FountainConfig::getSoKeepalive, true);
        int writeBufLow     = configOr(FountainConfig::getWriteBufferLow, 32 * 1024);
        int writeBufHigh    = configOr(FountainConfig::getWriteBufferHigh, 64 * 1024);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NativeTransport.serverChannelClass())
                .childHandler(new FountainChannelInitializer(router, virtualThreadPool,
                    concurrencyLimiter))
                // ---- Server socket options ----
                .option(ChannelOption.SO_BACKLOG, soBacklog)
                .option(ChannelOption.SO_REUSEADDR, soReuseaddr)
                // ---- Child (connection) socket options ----
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childOption(ChannelOption.SO_KEEPALIVE, soKeepalive)
                .childOption(ChannelOption.TCP_NODELAY, tcpNoDelay)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
                        new WriteBufferWaterMark(writeBufLow, writeBufHigh));

        serverChannel = bootstrap.bind(port).sync().channel();
        log.info("Fountain server started on port {} (transport: {}, max concurrency: {})",
                port, NativeTransport.name(), maxConcurrency);
    }

    private <T> T configOr(Function<FountainConfig, T> getter, T defaultValue) {
        return (config != null) ? getter.apply(config) : defaultValue;
    }

    public void awaitTermination() throws InterruptedException {
        if (serverChannel != null) {
            serverChannel.closeFuture().sync();
        }
    }

    public void stop() {
        log.info("Shutting down Fountain server...");
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (virtualThreadPool != null) {
            virtualThreadPool.close();
        }
    }
}
