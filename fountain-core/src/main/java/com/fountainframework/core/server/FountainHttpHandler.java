package com.fountainframework.core.server;

import com.fountainframework.core.http.FountainPoolRequest;
import com.fountainframework.core.http.HttpHeaders;
import com.fountainframework.core.http.HttpMethod;
import com.fountainframework.core.http.HttpResponse;
import com.fountainframework.core.http.HttpVersion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

/**
 * Netty channel handler that converts Netty HTTP objects to Fountain types,
 * dispatches to the route handler, and writes the response.
 */
public class FountainHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger log = LoggerFactory.getLogger(FountainHttpHandler.class);
    private final RequestDispatcher dispatcher;

    public FountainHttpHandler(RequestDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest nettyRequest) {
        FountainPoolRequest request = convertRequest(ctx, nettyRequest);
        log.debug("{} {} from {}", request.method(), request.path(), request.remoteAddress());

        HttpResponse response = dispatcher.dispatch(request);
        writeResponse(ctx, request, response);
    }

    private FountainPoolRequest convertRequest(ChannelHandlerContext ctx, FullHttpRequest nettyRequest) {
        HttpHeaders headers = new HttpHeaders();
        for (Map.Entry<String, String> entry : nettyRequest.headers()) {
            headers.add(entry.getKey(), entry.getValue());
        }

        byte[] body = new byte[0];
        ByteBuf content = nettyRequest.content();
        if (content.isReadable()) {
            body = new byte[content.readableBytes()];
            content.readBytes(body);
        }

        String remoteAddress = "";
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress inet) {
            remoteAddress = inet.getAddress().getHostAddress();
        }

        return FountainPoolRequest.builder()
                .method(HttpMethod.resolve(nettyRequest.method().name()))
                .uri(nettyRequest.uri())
                .version(HttpVersion.resolve(nettyRequest.protocolVersion().text()))
                .headers(headers)
                .body(body)
                .remoteAddress(remoteAddress)
                .build();
    }

    private void writeResponse(ChannelHandlerContext ctx, FountainPoolRequest request, HttpResponse response) {
        byte[] body = response.body();
        ByteBuf content = Unpooled.wrappedBuffer(body);

        DefaultFullHttpResponse nettyResponse = new DefaultFullHttpResponse(
                io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(response.statusCode()),
                content
        );

        // Copy headers
        for (Map.Entry<String, List<String>> entry : response.headers()) {
            for (String value : entry.getValue()) {
                nettyResponse.headers().add(entry.getKey(), value);
            }
        }
        nettyResponse.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, body.length);

        boolean keepAlive = request.isKeepAlive();
        if (keepAlive) {
            nettyResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.writeAndFlush(nettyResponse);
        } else {
            ctx.writeAndFlush(nettyResponse).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Unhandled exception in channel pipeline", cause);
        ctx.close();
    }
}
