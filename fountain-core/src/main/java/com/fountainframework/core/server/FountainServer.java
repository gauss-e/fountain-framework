package com.fountainframework.core.server;

import com.fountainframework.core.router.Router;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Netty-based HTTP server with virtual-thread-per-task execution.
 * <p>
 * Netty NIO event loops handle I/O (accept, read, write).
 * Handler business logic is dispatched to virtual threads — one per request.
 * A {@link Semaphore} caps concurrency to prevent unbounded resource usage under load.
 * Default max concurrency: 1000, configurable via constructor.
 */
public class FountainServer {

    private static final Logger log = LoggerFactory.getLogger(FountainServer.class);
    private static final int DEFAULT_MAX_CONCURRENCY = 1000;

    private final int port;
    private final Router router;
    private final int maxConcurrency;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ExecutorService virtualThreadPool;
    private Semaphore concurrencyLimiter;
    private Channel serverChannel;

    public FountainServer(int port, Router router) {
        this(port, router, DEFAULT_MAX_CONCURRENCY);
    }

    public FountainServer(int port, Router router, int maxConcurrency) {
        this.port = port;
        this.router = router;
        this.maxConcurrency = maxConcurrency;
    }

    public void start() throws InterruptedException {
        virtualThreadPool = Executors.newVirtualThreadPerTaskExecutor();
        concurrencyLimiter = new Semaphore(maxConcurrency);

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new FountainChannelInitializer(router, virtualThreadPool, concurrencyLimiter))
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        serverChannel = bootstrap.bind(port).sync().channel();
        log.info("Fountain server started on port {} (max concurrency: {})", port, maxConcurrency);
    }

    public Semaphore concurrencyLimiter() {
        return concurrencyLimiter;
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
