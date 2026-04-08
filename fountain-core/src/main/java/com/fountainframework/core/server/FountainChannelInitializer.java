package com.fountainframework.core.server;

import com.fountainframework.core.router.Router;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpServerCodec;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

/**
 * Configures the Netty channel pipeline for HTTP request handling.
 * <p>
 * Pipeline stages:
 * <ol>
 *   <li>{@code httpCodec} — HTTP request/response codec</li>
 *   <li>{@code httpCompressor} — gzip/deflate content compression
 *       (negotiated via Accept-Encoding)</li>
 *   <li>{@code handler} — streaming request handler with backpressure
 *       (no {@code HttpObjectAggregator} — body chunks are accumulated
 *       incrementally in {@link FountainHttpHandler})</li>
 * </ol>
 */
public class FountainChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final Router router;
    private final ExecutorService virtualThreadPool;
    private final Semaphore concurrencyLimiter;

    public FountainChannelInitializer(Router router, ExecutorService virtualThreadPool, Semaphore concurrencyLimiter) {
        this.router = router;
        this.virtualThreadPool = virtualThreadPool;
        this.concurrencyLimiter = concurrencyLimiter;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("httpCodec", new HttpServerCodec());
        pipeline.addLast("httpCompressor", new HttpContentCompressor());
        pipeline.addLast("handler", new FountainHttpHandler(router, virtualThreadPool, concurrencyLimiter));
    }
}
