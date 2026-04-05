package com.fountainframework.core.server;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

/**
 * Configures the Netty channel pipeline for HTTP request handling.
 */
public class FountainChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final int MAX_CONTENT_LENGTH = 1024 * 1024; // 1 MB

    private final RequestDispatcher dispatcher;

    public FountainChannelInitializer(RequestDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("httpCodec", new HttpServerCodec());
        pipeline.addLast("httpAggregator", new HttpObjectAggregator(MAX_CONTENT_LENGTH));
        pipeline.addLast("handler", new FountainHttpHandler(dispatcher));
    }
}
