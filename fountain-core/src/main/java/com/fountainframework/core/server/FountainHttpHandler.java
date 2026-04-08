package com.fountainframework.core.server;

import com.fountainframework.core.http.FountainPoolRequest;
import com.fountainframework.core.http.FountainRequest;
import com.fountainframework.core.http.HttpResponse;
import com.fountainframework.core.router.Router;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

/**
 * Netty channel handler — the orchestration hub of Fountain's request lifecycle.
 * <p>
 * Responsibilities are delegated to focused collaborators:
 * <ul>
 *   <li>{@link BackpressureController} — lock-free backpressure coordination
 *       (CAS flag, park/unpark, request queueing)</li>
 *   <li>{@link ResponseEncoder} — response head construction, chunked streaming
 *       write with per-chunk writability checks</li>
 * </ul>
 * <p>
 * This handler itself only orchestrates:
 * <ol>
 *   <li>Streaming request read: accumulate {@link HttpContent} chunks into a
 *       zero-copy {@link CompositeByteBuf}</li>
 *   <li>Dispatch: assemble a {@link FountainRequest}, acquire a concurrency permit,
 *       submit to virtual thread pool (or queue under backpressure)</li>
 *   <li>Route + write: execute handler on virtual thread, stream the response back</li>
 * </ol>
 */
public class FountainHttpHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(FountainHttpHandler.class);
    private static final int MAX_QUEUED_REQUESTS = 256;

    private final Router router;
    private final ExecutorService virtualThreadPool;
    private final Semaphore concurrencyLimiter;
    private final BackpressureController backpressure;
    private final ResponseEncoder responseEncoder;

    // ---- Per-connection streaming state (Netty I/O thread only) ----
    private HttpRequest currentNettyRequest;
    private CompositeByteBuf bodyAccumulator;
    private boolean keepAlive;

    public FountainHttpHandler(Router router, ExecutorService virtualThreadPool, Semaphore concurrencyLimiter) {
        this.router = router;
        this.virtualThreadPool = virtualThreadPool;
        this.concurrencyLimiter = concurrencyLimiter;
        this.backpressure = new BackpressureController(MAX_QUEUED_REQUESTS);
        this.responseEncoder = new ResponseEncoder(backpressure);
    }

    // ========================================================================
    // Read side — Netty I/O thread
    // ========================================================================

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest httpRequest) {
            handleRequestHead(ctx, httpRequest);
        }
        if (msg instanceof HttpContent httpContent) {
            handleContentChunk(ctx, httpContent);
        }
    }

    private void handleRequestHead(ChannelHandlerContext ctx, HttpRequest httpRequest) {
        releaseAccumulator();
        currentNettyRequest = httpRequest;
        keepAlive = HttpUtil.isKeepAlive(httpRequest);
        bodyAccumulator = ctx.alloc().compositeBuffer();
    }

    private void handleContentChunk(ChannelHandlerContext ctx, HttpContent httpContent) {
        if (currentNettyRequest == null) {
            httpContent.release();
            return;
        }

        ByteBuf chunk = httpContent.content();
        if (chunk.isReadable()) {
            bodyAccumulator.addComponent(true, chunk.retain());
        }

        if (httpContent instanceof LastHttpContent) {
            assembleAndDispatch(ctx);
        }
    }

    // ========================================================================
    // Dispatch
    // ========================================================================

    private void assembleAndDispatch(ChannelHandlerContext ctx) {
        HttpRequest nettyReq = currentNettyRequest;
        CompositeByteBuf body = bodyAccumulator;
        boolean currentKeepAlive = keepAlive;

        currentNettyRequest = null;
        bodyAccumulator = null;

        String remoteAddress = "";
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress inet) {
            remoteAddress = inet.getAddress().getHostAddress();
        }

        FountainPoolRequest request = new FountainPoolRequest(nettyReq, body, remoteAddress);

        log.debug("{} {} from {}", request.method(), request.path(), request.remoteAddress());

        if (!concurrencyLimiter.tryAcquire()) {
            log.warn("Server overloaded, rejecting {} {}", request.method(), request.path());
            request.release();
            responseEncoder.writeImmediate(ctx, currentKeepAlive, HttpResponse.serviceUnavailable());
            return;
        }

        Runnable task = () -> {
            try {
                HttpResponse response = dispatch(request);
                responseEncoder.writeResponse(ctx, currentKeepAlive, response);
            } finally {
                request.release();
                concurrencyLimiter.release();
            }
        };

        if (backpressure.isBackpressure()) {
            if (!backpressure.enqueue(ctx, task)) {
                log.warn("Backpressure queue full, rejecting {} {}", request.method(), request.path());
                request.release();
                concurrencyLimiter.release();
                responseEncoder.writeImmediate(ctx, currentKeepAlive, HttpResponse.serviceUnavailable());
            }
        } else {
            virtualThreadPool.execute(task);
        }
    }

    private HttpResponse dispatch(FountainRequest request) {
        try {
            HttpResponse response = router.handle(request);
            return response != null ? response : HttpResponse.notFound();
        } catch (Exception e) {
            log.error("Error handling {} {}", request.method(), request.path(), e);
            return HttpResponse.error("Internal Server Error");
        }
    }

    // ========================================================================
    // Writability change — Netty I/O thread
    // ========================================================================

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isWritable()) {
            backpressure.onWritable(ctx, virtualThreadPool);
        }
        super.channelWritabilityChanged(ctx);
    }

    // ========================================================================
    // Lifecycle
    // ========================================================================

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        releaseAccumulator();
        backpressure.shutdown();
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Unhandled exception in channel pipeline", cause);
        releaseAccumulator();
        ctx.close();
    }

    private void releaseAccumulator() {
        if (bodyAccumulator != null && bodyAccumulator.refCnt() > 0) {
            bodyAccumulator.release();
            bodyAccumulator = null;
        }
    }
}
