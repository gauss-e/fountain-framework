package com.fountainframework.core.server;

import com.fountainframework.core.http.FountainPoolRequest;
import com.fountainframework.core.http.HttpHeaders;
import com.fountainframework.core.http.HttpMethod;
import com.fountainframework.core.http.HttpResponse;
import com.fountainframework.core.http.HttpVersion;
import com.fountainframework.core.router.Router;
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
import java.util.concurrent.ExecutorService;

/**
 * Netty channel handler that converts Netty HTTP objects to Fountain types
 * and dispatches handler execution to the virtual thread pool.
 * <p>
 * Netty I/O thread: parse request -> Virtual thread: run handler -> Netty I/O thread: write response
 * <p>
 * Concurrency is bounded by the fixed-size virtual thread pool — when all threads
 * are busy, new tasks queue until a thread becomes available.
 */
public class FountainHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger log = LoggerFactory.getLogger(FountainHttpHandler.class);
    private final Router router;
    private final ExecutorService virtualThreadPool;

    public FountainHttpHandler(Router router, ExecutorService virtualThreadPool) {
        this.router = router;
        this.virtualThreadPool = virtualThreadPool;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest nettyRequest) {
        FountainPoolRequest request = convertRequest(ctx, nettyRequest);
        boolean keepAlive = request.isKeepAlive();

        log.debug("{} {} from {}", request.method(), request.path(), request.remoteAddress());

        virtualThreadPool.execute(() -> {
            HttpResponse response;
            try {
                response = router.handle(request);
                if (response == null) {
                    response = HttpResponse.notFound();
                }
            } catch (Exception e) {
                log.error("Error handling {} {}", request.method(), request.path(), e);
                response = HttpResponse.error("Internal Server Error");
            }

            HttpResponse finalResponse = response;
            ctx.channel().eventLoop().execute(() ->
                    writeResponse(ctx, keepAlive, finalResponse));
        });
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

    private void writeResponse(ChannelHandlerContext ctx, boolean keepAlive, HttpResponse response) {
        byte[] body = response.body();
        ByteBuf content = Unpooled.wrappedBuffer(body);

        DefaultFullHttpResponse nettyResponse = new DefaultFullHttpResponse(
                io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(response.statusCode()),
                content
        );

        for (Map.Entry<String, List<String>> entry : response.headers()) {
            for (String value : entry.getValue()) {
                nettyResponse.headers().add(entry.getKey(), value);
            }
        }
        nettyResponse.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, body.length);

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
