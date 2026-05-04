package io.github.bitpart.smus.server;

import io.github.bitpart.smus.protocol.SmusMessage;

public interface SmusServerListener {
    default void onLogon(User user) {}

    default void onLogout(User user) {}

    default void onMessage(User sender, SmusMessage message) {}

    default void onError(Throwable error) {
        error.printStackTrace();
    }
}
