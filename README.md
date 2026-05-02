# jSMUS

jSMUS is a JDK 21 Java port of Bitpart's Shockwave Multiuser Server protocol support. It is designed to be used as a normal Java library and also includes a runnable server entry point.

It includes:

- Lingo binary value encoding and decoding
- SMUS packet framing
- Adobe-compatible Blowfish transform for encrypted login packets
- A virtual-threaded embedded SMUS server
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
import io.github.bitpart.smus.SmusServer;
import io.github.bitpart.smus.SmusServerConfig;

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
var server = new SmusServer(config, new SmusServerListener() {
    @Override
    public void onLogon(SmusServer.User user) {
        System.out.println(user.name() + " joined " + user.movie());
    }

    @Override
    public void onMessage(SmusServer.User sender, SmusMessage message) {
        System.out.println(sender.name() + ": " + message.subject());
    }
});
```

Encode Lingo values and SMUS messages:

```java
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

The Java module is `io.github.bitpart.smus` and exports `io.github.bitpart.smus`.

Primary classes:

- `SmusServer`
- `SmusServerConfig`
- `SmusServerListener`
- `SmusMessage`
- `SmusCodec`
- `LValue`
- `LingoCodec`
- `SmusBlowfish`

## License

jSMUS uses the same license as Bitpart. See `LICENSE`.
