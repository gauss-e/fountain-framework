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
 * Netty-based HTTP server with virtual thread support.
 * <p>
 * Netty NIO event loops handle I/O (accept, read, write).
 * Handler business logic is dispatched to virtual threads for non-blocking execution.
 * Default concurrency limit: 1000 virtual threads.
 */
public class FountainServer {

    private static final Logger log = LoggerFactory.getLogger(FountainServer.class);
    private static final int DEFAULT_MAX_VIRTUAL_THREADS = 1000;

    private final int port;
    private final Router router;
    private final int maxVirtualThreads;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ExecutorService virtualThreadExecutor;
    private Semaphore concurrencyLimiter;
    private Channel serverChannel;

    public FountainServer(int port, Router router) {
        this(port, router, DEFAULT_MAX_VIRTUAL_THREADS);
    }

    public FountainServer(int port, Router router, int maxVirtualThreads) {
        this.port = port;
        this.router = router;
        this.maxVirtualThreads = maxVirtualThreads;
    }

    public void start() throws InterruptedException {
        virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        concurrencyLimiter = new Semaphore(maxVirtualThreads);

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new FountainChannelInitializer(router, virtualThreadExecutor, concurrencyLimiter))
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        serverChannel = bootstrap.bind(port).sync().channel();
        log.info("Fountain server started on port {} (virtual threads: max {})", port, maxVirtualThreads);
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
        if (virtualThreadExecutor != null) {
            virtualThreadExecutor.close();
        }
    }
}
