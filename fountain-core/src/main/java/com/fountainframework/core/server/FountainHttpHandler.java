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
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

/**
 * Netty channel handler that converts Netty HTTP objects to Fountain types
 * and dispatches handler execution to virtual threads.
 * <p>
 * Netty I/O thread: parse request -> Virtual thread: run handler -> Netty I/O thread: write response
 * <p>
 * Concurrency is bounded by a {@link Semaphore}. When the limit is reached,
 * new requests are immediately rejected with 503 Service Unavailable instead of
 * queuing unboundedly, preventing memory pressure and tail-latency degradation.
 */
public class FountainHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger log = LoggerFactory.getLogger(FountainHttpHandler.class);
    private final Router router;
    private final ExecutorService virtualThreadPool;
    private final Semaphore concurrencyLimiter;

    public FountainHttpHandler(Router router, ExecutorService virtualThreadPool, Semaphore concurrencyLimiter) {
        this.router = router;
        this.virtualThreadPool = virtualThreadPool;
        this.concurrencyLimiter = concurrencyLimiter;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest nettyRequest) {
        // Retain the request so its ByteBuf and headers survive past channelRead0.
        // The virtual thread's finally block will release via request.release().
        nettyRequest.retain();
        FountainPoolRequest request = convertRequest(ctx, nettyRequest);
        boolean keepAlive = HttpUtil.isKeepAlive(nettyRequest);

        log.debug("{} {} from {}", request.method(), request.path(), request.remoteAddress());

        if (!concurrencyLimiter.tryAcquire()) {
            log.warn("Server overloaded, rejecting {} {}", request.method(), request.path());
            request.release();
            writeResponse(ctx, keepAlive, HttpResponse.serviceUnavailable());
            return;
        }

        virtualThreadPool.execute(() -> {
            try {
                // Netty's writeAndFlush is thread-safe — it internally routes to the
                // event loop, so an explicit eventLoop().execute() wrapper is redundant
                // and adds an unnecessary queuing hop.
                writeResponse(ctx, keepAlive, dispatch(request));
            } finally {
                request.release();
                concurrencyLimiter.release();
            }
        });
    }

    private HttpResponse dispatch(FountainPoolRequest request) {
        try {
            HttpResponse response = router.handle(request);
            return response != null ? response : HttpResponse.notFound();
        } catch (Exception e) {
            log.error("Error handling {} {}", request.method(), request.path(), e);
            return HttpResponse.error("Internal Server Error");
        }
    }

    private FountainPoolRequest convertRequest(ChannelHandlerContext ctx, FullHttpRequest nettyRequest) {
        // Zero-copy header wrapping — delegates reads to Netty's headers directly
        HttpHeaders headers = HttpHeaders.wrap(nettyRequest.headers());

        // Zero-copy body — hold the ByteBuf reference; byte[] is materialized
        // lazily only when the handler actually calls body(). GET requests and
        // other no-body requests skip allocation entirely.
        ByteBuf content = nettyRequest.content();

        String remoteAddress = "";
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress inet) {
            remoteAddress = inet.getAddress().getHostAddress();
        }

        return FountainPoolRequest.builder()
                .method(HttpMethod.resolve(nettyRequest.method().name()))
                .uri(nettyRequest.uri())
                .version(HttpVersion.resolve(nettyRequest.protocolVersion().text()))
                .headers(headers)
                .bodyBuf(content)
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
            ctx.write(nettyResponse);
        } else {
            ctx.write(nettyResponse).addListener(ChannelFutureListener.CLOSE);
        }
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Unhandled exception in channel pipeline", cause);
        ctx.close();
    }
}
