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

/**
 * Netty-based HTTP server with a fixed virtual thread pool.
 * <p>
 * Netty NIO event loops handle I/O (accept, read, write).
 * Handler business logic is dispatched to a pre-created pool of virtual threads.
 * Default pool size: 1000 virtual threads, configurable via {@code fountain.server.virtualthread.num}.
 */
public class FountainServer {

    private static final Logger log = LoggerFactory.getLogger(FountainServer.class);
    private static final int DEFAULT_VIRTUAL_THREAD_NUM = 1000;

    private final int port;
    private final Router router;
    private final int virtualThreadNum;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ExecutorService virtualThreadPool;
    private Channel serverChannel;

    public FountainServer(int port, Router router) {
        this(port, router, DEFAULT_VIRTUAL_THREAD_NUM);
    }

    public FountainServer(int port, Router router, int virtualThreadNum) {
        this.port = port;
        this.router = router;
        this.virtualThreadNum = virtualThreadNum;
    }

    public void start() throws InterruptedException {
        virtualThreadPool = Executors.newFixedThreadPool(virtualThreadNum,
                Thread.ofVirtual().name("fountain-vt-", 0).factory());

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new FountainChannelInitializer(router, virtualThreadPool))
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        serverChannel = bootstrap.bind(port).sync().channel();
        log.info("Fountain server started on port {} (virtual thread pool: {})", port, virtualThreadNum);
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
