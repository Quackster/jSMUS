# jSMUS

jSMUS is a JDK 21 Java port of Bitpart's Shockwave Multiuser Server protocol support. It is designed to be used as a normal Java library and also includes a runnable server entry point.

It includes:

- Lingo binary value encoding and decoding
- SMUS packet framing
- Adobe-compatible Blowfish transform for encrypted login packets
- A Netty-based embedded SMUS server
- A small CLI server launcher

## Requirements

- JDK 21 or newer
- Gradle, unless your consuming project pulls the artifact through JitPack

## Build

From this folder:

```powershell
gradle test
gradle build
```

Run the server:

```powershell
gradle run --args="1626 IPAddress resolution"
```

The first argument is the port. The second argument is the SMUS encryption key used for login packets.

## Use As A Library

```java
import io.github.bitpart.smus.server.SmusServer;
import io.github.bitpart.smus.server.SmusServerConfig;

public final class Main {
    public static void main(String[] args) throws Exception {
        var config = SmusServerConfig.anyAddress(1626, "IPAddress resolution");

        try (var server = new SmusServer(config)) {
            server.start();
            Thread.currentThread().join();
        }
    }
}
```

Listen for server events:

```java
import io.github.bitpart.smus.protocol.SmusMessage;
import io.github.bitpart.smus.server.SmusServerListener;
import io.github.bitpart.smus.server.User;

var server = new SmusServer(config, new SmusServerListener() {
    @Override
    public void onLogon(User user) {
        System.out.println(user.name() + " joined " + user.movie());
    }

    @Override
    public void onMessage(User sender, SmusMessage message) {
        System.out.println(sender.name() + ": " + message.subject());
    }
});
```

Encode Lingo values and SMUS messages:

```java
import io.github.bitpart.smus.protocol.LValue;
import io.github.bitpart.smus.protocol.LingoCodec;
import io.github.bitpart.smus.protocol.SmusCodec;
import io.github.bitpart.smus.protocol.SmusMessage;

byte[] content = LingoCodec.encode(new LValue.StringValue("hello"));
SmusMessage message = SmusMessage.of("System", List.of("SomeUser"), "Greeting", content);
byte[] frame = SmusCodec.pack(message);
```

## JitPack

This repository is configured for JitPack with the root `jitpack.yml`, which builds the `jSMUS` subfolder using JDK 21. The Gradle publication uses JitPack's `GROUP`, `ARTIFACT`, and `VERSION` environment variables when they are present, so the dependency coordinate follows the standard JitPack repository format.

Add JitPack to your Gradle repositories:

```groovy
repositories {
    mavenCentral()
    maven { url "https://jitpack.io" }
}
```

Use Quackster's GitHub repository coordinates:

```groovy
dependencies {
    implementation "com.github.Quackster:jSMUS:TAG"
}
```

Replace `TAG` with a release tag, branch, or commit hash. For example:

```groovy
dependencies {
    implementation "com.github.Quackster:jSMUS:main-SNAPSHOT"
}
```

If the GitHub repository name changes, replace `jSMUS` in the dependency coordinate with the actual repository name.

## Published API

The Java module is `io.github.bitpart.smus`. The public API is split across `io.github.bitpart.smus.server`, `io.github.bitpart.smus.protocol`, and `io.github.bitpart.smus.crypto`.

Primary classes:

- `server.SmusServer`
- `server.SmusServerConfig`
- `server.SmusServerListener`
- `server.User`
- `protocol.SmusMessage`
- `protocol.SmusCodec`
- `protocol.LValue`
- `protocol.LingoCodec`
- `crypto.SmusBlowfish`

## License

jSMUS uses the same license as Bitpart. See `LICENSE`.
