package io.github.bitpart.smus.server;

import io.github.bitpart.smus.protocol.SmusCodec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.TooLongFrameException;

import java.util.List;

final class SmusFrameDecoder extends ByteToMessageDecoder {
    private final int maxFrameBytes;

    SmusFrameDecoder(int maxFrameBytes) {
        this.maxFrameBytes = maxFrameBytes;
    }

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < SmusCodec.HEADER_SIZE) {
            return;
        }

        in.markReaderIndex();
        byte[] header = new byte[SmusCodec.HEADER_SIZE];
        in.readBytes(header);
        int frameLength = SmusCodec.frameLength(header);
        if (frameLength < 0) {
            throw new CorruptedFrameException("SMUS frame length must not be negative");
        }
        if (frameLength > maxFrameBytes) {
            throw new TooLongFrameException("SMUS frame exceeds maxFrameBytes");
        }
        if (in.readableBytes() < frameLength) {
            in.resetReaderIndex();
            return;
        }

        byte[] body = new byte[frameLength];
        in.readBytes(body);
        out.add(body);
    }
}
