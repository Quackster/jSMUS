module io.github.bitpart.smus {
    requires io.netty.buffer;
    requires io.netty.codec;
    requires io.netty.common;
    requires io.netty.transport;

    exports io.github.bitpart.smus.crypto;
    exports io.github.bitpart.smus.protocol;
    exports io.github.bitpart.smus.server;
}
