package io.github.bitpart.smus.server;

import io.github.bitpart.smus.protocol.SmusCodec;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

final class SmusChannelHandler extends SimpleChannelInboundHandler<byte[]> {
    private final SmusServer server;
    private Session session;

    SmusChannelHandler(SmusServer server) {
        this.server = server;
    }

    @Override
    public void channelActive(ChannelHandlerContext context) {
        session = new Session(context.channel(), server.encryptionKey());
        server.addSession(session);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, byte[] body) {
        server.handle(session, SmusCodec.unpack(body, session.authenticated ? null : server.loginKey()));
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) {
        if (session != null) {
            server.removeSession(session);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        if (server.isRunning()) {
            server.listener().onError(cause);
        }
        context.close();
    }
}
