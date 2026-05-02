package io.github.bitpart.smus;

public interface SmusServerListener {
    default void onLogon(SmusServer.User user) {}

    default void onLogout(SmusServer.User user) {}

    default void onMessage(SmusServer.User sender, SmusMessage message) {}

    default void onError(Throwable error) {
        error.printStackTrace();
    }
}
