package io.github.bitpart.smus.server;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

final class SmusChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final SmusServer server;
    private final int maxFrameBytes;

    SmusChannelInitializer(SmusServer server, int maxFrameBytes) {
        this.server = server;
        this.maxFrameBytes = maxFrameBytes;
    }

    @Override
    protected void initChannel(SocketChannel channel) {
        channel.pipeline()
                .addLast(new SmusFrameDecoder(maxFrameBytes))
                .addLast(new SmusChannelHandler(server));
    }
}
