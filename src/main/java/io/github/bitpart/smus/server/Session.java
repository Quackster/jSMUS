package io.github.bitpart.smus.server;

import io.github.bitpart.smus.protocol.SmusCodec;
import io.github.bitpart.smus.protocol.SmusMessage;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

final class Session {
    final String id = HexFormat.of().formatHex(UUID.randomUUID().toString().getBytes()).substring(0, 24);
    private final Channel channel;
    private final String encryptionKey;
    private final int[] lastMessages = new int[100];
    volatile boolean authenticated;
    volatile int protocolMajor;
    volatile int protocolMinor;
    volatile int clientMajor;
    volatile int clientMinor;
    private volatile int messageHashIndex = -1;
    private volatile byte[] messageHash = new byte[0];

    Session(Channel channel, String encryptionKey) {
        this.channel = channel;
        this.encryptionKey = encryptionKey;
    }

    void send(SmusMessage message) {
        channel.writeAndFlush(Unpooled.wrappedBuffer(SmusCodec.pack(message)))
                .addListener(future -> {
                    if (!future.isSuccess()) {
                        close();
                    }
                });
    }

    void close() {
        channel.close();
    }

    boolean checkOrder(int messageId) {
        int index = Math.floorMod(messageId, lastMessages.length);
        int previous = lastMessages[index];
        if (previous == messageId || previous > messageId) {
            return false;
        }
        lastMessages[index] = messageId;
        return true;
    }

    int expectedHash(int order) {
        int block = Math.floorDiv(order, 16);
        int index = Math.floorMod(order, 16);
        if (messageHashIndex != block) {
            try {
                MessageDigest md5 = MessageDigest.getInstance("MD5");
                messageHash = md5.digest((encryptionKey + id + block).getBytes(StandardCharsets.ISO_8859_1));
                messageHashIndex = block;
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
        return messageHash[index] & 0xff;
    }
}
