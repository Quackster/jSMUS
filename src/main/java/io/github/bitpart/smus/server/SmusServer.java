package io.github.bitpart.smus.server;

import io.github.bitpart.smus.crypto.SmusBlowfish;
import io.github.bitpart.smus.protocol.LValue;
import io.github.bitpart.smus.protocol.LingoCodec;
import io.github.bitpart.smus.protocol.SmusMessage;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SmusServer implements AutoCloseable {
    private static final String ALL_USERS = "@AllUsers";

    private final SmusServerConfig config;
    private final SmusServerListener listener;
    private final SmusBlowfish.KeySchedule loginKey;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, User> usersBySession = new ConcurrentHashMap<>();
    private final MultiThreadIoEventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    private final MultiThreadIoEventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    private volatile Channel serverChannel;

    public SmusServer(SmusServerConfig config) {
        this(config, new SmusServerListener() {});
    }

    public SmusServer(SmusServerConfig config, SmusServerListener listener) {
        this.config = Objects.requireNonNull(config, "config");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.loginKey = SmusBlowfish.keySchedule(config.encryptionKey());
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            ChannelFuture bind = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channelFactory(NioServerSocketChannel::new)
                    .childHandler(new SmusChannelInitializer(this, config.maxFrameBytes()))
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .bind(new InetSocketAddress(config.bindAddress(), config.port()))
                    .sync();
            serverChannel = bind.channel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running.set(false);
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            throw new IOException("Interrupted while starting SMUS server", e);
        } catch (RuntimeException e) {
            running.set(false);
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            throw e;
        }
    }

    public List<User> users() {
        return List.copyOf(usersBySession.values());
    }

    public void send(String sessionId, SmusMessage message) {
        Session session = sessions.get(sessionId);
        if (session != null) {
            session.send(message);
        }
    }

    public void broadcast(SmusMessage message) {
        sessions.values().forEach(session -> session.send(message));
    }

    @Override
    public void close() {
        running.set(false);
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        sessions.values().forEach(Session::close);
        bossGroup.shutdownGracefully().syncUninterruptibly();
        workerGroup.shutdownGracefully().syncUninterruptibly();
    }

    void handle(Session session, SmusMessage message) {
        if (!session.authenticated) {
            handleLogon(session, message);
            return;
        }
        if (!isValidMessage(session, message)) {
            session.close();
            return;
        }
        User sender = usersBySession.get(session.id);
        if (sender == null) {
            session.close();
            return;
        }
        listener.onMessage(sender, message);
        if (handleSystemMessage(session, sender, message)) {
            return;
        }
        routeUserMessage(sender, message);
    }

    void addSession(Session session) {
        sessions.put(session.id, session);
    }

    void removeSession(Session session) {
        sessions.remove(session.id);
        User user = usersBySession.remove(session.id);
        if (user != null) {
            listener.onLogout(user);
        }
    }

    boolean isRunning() {
        return running.get();
    }

    SmusBlowfish.KeySchedule loginKey() {
        return loginKey;
    }

    String encryptionKey() {
        return config.encryptionKey();
    }

    SmusServerListener listener() {
        return listener;
    }

    private void handleLogon(Session session, SmusMessage message) {
        LValue content = LingoCodec.decode(message.content());
        Login login = Login.from(content).orElseThrow(() -> new IllegalArgumentException("Logon content must be a list or property list"));
        String existingSession = usersBySession.values().stream()
                .filter(user -> user.name().equalsIgnoreCase(login.userId()) && user.movie().equalsIgnoreCase(login.movieId()))
                .map(User::sessionId)
                .findFirst()
                .orElse(null);
        if (existingSession != null) {
            Optional.ofNullable(sessions.get(existingSession)).ifPresent(Session::close);
        }
        session.authenticated = true;
        session.protocolMajor = login.protocolMajor();
        session.protocolMinor = login.protocolMinor();
        session.clientMajor = login.clientMajor();
        session.clientMinor = login.clientMinor();

        User user = new User(login.userId(), login.movieId(), Set.of(ALL_USERS), session.id);
        usersBySession.put(session.id, user);
        session.send(SmusMessage.of("System", List.of(user.name()), "Logon", LingoCodec.encode(new LValue.StringValue(user.movie()))));
        if (session.protocolMajor > 1 || (session.protocolMajor == 1 && session.protocolMinor > 1)) {
            session.send(SmusMessage.of("System", List.of(user.name()), "SessionID", LingoCodec.encode(new LValue.StringValue(session.id))));
        }
        listener.onLogon(user);
    }

    private boolean handleSystemMessage(Session session, User sender, SmusMessage message) {
        if (message.recipients().size() != 1) {
            return false;
        }
        Recipient recipient = Recipient.parse(message.recipients().getFirst());
        String command = recipient.name().toLowerCase(Locale.ROOT);
        LValue content = LingoCodec.decode(message.content());
        switch (command) {
            case "system.group.getusers" -> {
                if (recipient.movie().isPresent() && content instanceof LValue.StringValue group) {
                    LValue result = groupMembers(recipient.movie().orElseThrow(), group.value());
                    reply(session, message, sender.name(), result);
                    return true;
                }
            }
            case "system.movie.getusercount" -> {
                recipient.movie().ifPresent(movie -> reply(session, message, sender.name(), new LValue.PropertyList(List.of(
                        new LValue.Property("movieID", new LValue.StringValue(movie)),
                        new LValue.Property("numberMembers", new LValue.IntegerValue((int) usersBySession.values().stream()
                                .filter(user -> user.movie().equalsIgnoreCase(movie)).count()))
                ))));
                return recipient.movie().isPresent();
            }
            case "system.server.getmovies" -> {
                reply(session, message, sender.name(), new LValue.ListValue(usersBySession.values().stream()
                        .map(User::movie)
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .map(LValue.StringValue::new)
                        .map(LValue.class::cast)
                        .toList()));
                return true;
            }
            case "system.user.delete" -> {
                session.close();
                return true;
            }
            case "system" -> {
                if (message.subject().equalsIgnoreCase("joinGroup") && content instanceof LValue.StringValue group) {
                    updateGroups(sender, sender.groupsPlus(group.value()));
                    return true;
                }
                if (message.subject().equalsIgnoreCase("leaveGroup") && content instanceof LValue.StringValue group) {
                    updateGroups(sender, sender.groupsMinus(group.value()));
                    return true;
                }
                if (message.subject().equalsIgnoreCase("getGroupMembers") && content instanceof LValue.StringValue group) {
                    reply(session, message, sender.name(), groupMembers(sender.movie(), group.value()));
                    return true;
                }
            }
            default -> {
            }
        }
        return false;
    }

    private void routeUserMessage(User sender, SmusMessage message) {
        for (String recipientText : message.recipients()) {
            Recipient recipient = Recipient.parse(recipientText);
            List<User> targets = usersBySession.values().stream()
                    .filter(user -> matches(sender, recipient, user))
                    .toList();
            String senderName = sender.name() + recipient.movie()
                    .filter(movie -> !movie.equalsIgnoreCase(sender.movie()))
                    .map(movie -> "@" + movie)
                    .orElse("");
            SmusMessage outbound = new SmusMessage(0, 0, message.subject(), senderName, message.recipients(), message.content());
            targets.forEach(user -> Optional.ofNullable(sessions.get(user.sessionId())).ifPresent(s -> s.send(outbound)));
        }
    }

    private boolean matches(User sender, Recipient recipient, User candidate) {
        Optional<String> movie = recipient.movie();
        if (movie.isPresent() && !candidate.movie().equalsIgnoreCase(movie.orElseThrow())) {
            return false;
        }
        if (movie.isEmpty() && !candidate.movie().equalsIgnoreCase(sender.movie())) {
            return false;
        }
        if (recipient.name().isEmpty()) {
            return false;
        }
        if (recipient.name().startsWith("@")) {
            return candidate.groups().stream().anyMatch(group -> group.equalsIgnoreCase(recipient.name()));
        }
        return candidate.name().equalsIgnoreCase(recipient.name());
    }

    private LValue groupMembers(String movie, String group) {
        String normalizedGroup = group.startsWith("@") ? group : "@" + group;
        return new LValue.PropertyList(List.of(
                new LValue.Property("groupName", new LValue.StringValue(group)),
                new LValue.Property("groupMembers", new LValue.ListValue(usersBySession.values().stream()
                        .filter(user -> user.movie().equalsIgnoreCase(movie))
                        .filter(user -> user.groups().stream().anyMatch(g -> g.equalsIgnoreCase(normalizedGroup)))
                        .map(User::name)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .map(LValue.StringValue::new)
                        .map(LValue.class::cast)
                        .toList()))
        ));
    }

    private void reply(Session session, SmusMessage request, String userName, LValue value) {
        session.send(SmusMessage.of(request.recipients().getFirst(), List.of(userName), request.subject(), LingoCodec.encode(value)));
    }

    private void updateGroups(User existing, Set<String> groups) {
        usersBySession.put(existing.sessionId(), new User(existing.name(), existing.movie(), groups, existing.sessionId()));
    }

    private boolean isValidMessage(Session session, SmusMessage message) {
        int order = message.errorCode();
        boolean hashOk = true;
        if (session.protocolMajor > 1 || (session.protocolMajor == 1 && session.protocolMinor > 1)) {
            byte[] bytes = new byte[] {
                    (byte) (message.errorCode() >>> 24),
                    (byte) (message.errorCode() >>> 16),
                    (byte) (message.errorCode() >>> 8),
                    (byte) message.errorCode()
            };
            order = ((bytes[0] & 0xff) << 16) | ((bytes[1] & 0xff) << 8) | (bytes[2] & 0xff);
            hashOk = (bytes[3] & 0xff) == session.expectedHash(order);
        }
        boolean orderedProtocol = session.protocolMajor > 1 || (session.protocolMajor == 1 && session.protocolMinor > 0);
        return hashOk && (!orderedProtocol || session.checkOrder(order));
    }
}
