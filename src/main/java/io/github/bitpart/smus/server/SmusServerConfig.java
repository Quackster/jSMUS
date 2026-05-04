package io.github.bitpart.smus.server;

import java.net.InetAddress;
import java.net.UnknownHostException;

public record SmusServerConfig(InetAddress bindAddress, int port, String encryptionKey, int maxFrameBytes) {
    public SmusServerConfig {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        if (encryptionKey == null || encryptionKey.isEmpty()) {
            throw new IllegalArgumentException("encryptionKey is required for login packets");
        }
        if (maxFrameBytes < 1) {
            throw new IllegalArgumentException("maxFrameBytes must be positive");
        }
    }

    public static SmusServerConfig localhost(int port, String encryptionKey) {
        try {
            return new SmusServerConfig(InetAddress.getByName("127.0.0.1"), port, encryptionKey, 1024 * 1024);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    public static SmusServerConfig anyAddress(int port, String encryptionKey) {
        try {
            return new SmusServerConfig(InetAddress.getByName("0.0.0.0"), port, encryptionKey, 1024 * 1024);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }
}
