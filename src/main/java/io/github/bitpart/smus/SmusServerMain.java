package io.github.bitpart.smus;

import java.util.concurrent.CountDownLatch;

public final class SmusServerMain {
    private SmusServerMain() {
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 1626;
        String key = args.length > 1 ? args[1] : "IPAddress resolution";
        var server = new SmusServer(SmusServerConfig.anyAddress(port, key));
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();
        System.out.printf("SMUS server listening on port %d%n", port);
        new CountDownLatch(1).await();
    }
}
