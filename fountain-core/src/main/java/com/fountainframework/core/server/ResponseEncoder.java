package com.fountainframework.core.server;

import com.fountainframework.core.http.HttpResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.util.List;
import java.util.Map;

/**
 * Encodes a {@link HttpResponse} into Netty HTTP objects and writes them
 * to the channel.
 * <p>
 * Two write modes:
 * <ul>
 *   <li>{@link #writeResponse} — streaming chunked write from a virtual thread,
 *       checking backpressure ({@link BackpressureController#awaitWritable}) before
 *       each chunk.</li>
 *   <li>{@link #writeImmediate} — single-shot write from the Netty I/O thread
 *       for small error responses (503, etc.) where streaming is unnecessary.</li>
 * </ul>
 */
final class ResponseEncoder {

    /** Chunk size for streaming response body writes (8 KB). */
    private static final int WRITE_CHUNK_SIZE = 8 * 1024;

    private final BackpressureController backpressure;

    ResponseEncoder(BackpressureController backpressure) {
        this.backpressure = backpressure;
    }

    /**
     * Large-body threshold: responses larger than this are written in chunks
     * with per-chunk backpressure checks to avoid flooding the write buffer.
     * Smaller responses use a single zero-copy wrappedBuffer write.
     */
    private static final int LARGE_BODY_THRESHOLD = 64 * 1024;

    /**
     * Stream the response to the channel from a virtual thread.
     * <p>
     * Small/medium bodies (≤ 64 KB): written as a single zero-copy
     * {@link Unpooled#wrappedBuffer} — no direct-buffer allocation, no copy.
     * <p>
     * Large bodies (> 64 KB): written in chunks with per-chunk backpressure
     * checks via {@link Unpooled#wrappedBuffer} slices (still zero-copy per chunk).
     */
    void writeResponse(ChannelHandlerContext ctx, boolean keepAlive, HttpResponse response) {
        if (!ctx.channel().isActive()) return;

        byte[] body = response.body();

        // 1. Response head
        backpressure.awaitWritable(ctx);
        if (!ctx.channel().isActive()) return;

        ctx.write(buildHead(response, body.length, keepAlive));

        // 2. Body — zero-copy via wrappedBuffer (no heap→direct copy)
        if (body.length > 0) {
            if (body.length <= LARGE_BODY_THRESHOLD) {
                // Single write — wraps the byte[] without copying
                backpressure.awaitWritable(ctx);
                if (!ctx.channel().isActive()) return;
                ctx.write(new DefaultHttpContent(Unpooled.wrappedBuffer(body)));
            } else {
                // Chunked write for large bodies — still zero-copy per chunk
                int offset = 0;
                while (offset < body.length) {
                    backpressure.awaitWritable(ctx);
                    if (!ctx.channel().isActive()) return;
                    int chunkLen = Math.min(WRITE_CHUNK_SIZE, body.length - offset);
                    ctx.write(new DefaultHttpContent(
                            Unpooled.wrappedBuffer(body, offset, chunkLen)));
                    offset += chunkLen;
                }
            }
        }

        // 3. End of response
        backpressure.awaitWritable(ctx);
        if (!ctx.channel().isActive()) return;

        flushLast(ctx, keepAlive);
    }

    /**
     * Single-shot write from the Netty I/O thread for small error responses.
     * No backpressure check — the response is tiny and must be sent immediately.
     */
    void writeImmediate(ChannelHandlerContext ctx, boolean keepAlive, HttpResponse response) {
        byte[] body = response.body();

        ctx.write(buildHead(response, body.length, keepAlive));

        if (body.length > 0) {
            // Zero-copy: wraps the byte[] without allocating a direct buffer
            ctx.write(new DefaultHttpContent(Unpooled.wrappedBuffer(body)));
        }

        flushLast(ctx, keepAlive);
    }

    // ---- Internal ----

    private DefaultHttpResponse buildHead(HttpResponse response, int contentLength, boolean keepAlive) {
        DefaultHttpResponse head = new DefaultHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(response.statusCode())
        );

        for (Map.Entry<String, List<String>> entry : response.headers()) {
            for (String value : entry.getValue()) {
                head.headers().add(entry.getKey(), value);
            }
        }
        head.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, contentLength);

        if (keepAlive) {
            head.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }

        return head;
    }

    private void flushLast(ChannelHandlerContext ctx, boolean keepAlive) {
        if (keepAlive) {
            ctx.writeAndFlush(new DefaultLastHttpContent());
        } else {
            ctx.writeAndFlush(new DefaultLastHttpContent())
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }
}
