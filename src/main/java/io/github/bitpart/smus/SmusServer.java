package io.github.bitpart.smus;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SmusServer implements AutoCloseable {
    private static final String ALL_USERS = "@AllUsers";

    private final SmusServerConfig config;
    private final SmusServerListener listener;
    private final SmusBlowfish.KeySchedule loginKey;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean running = new AtomicBoolean();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, User> usersBySession = new ConcurrentHashMap<>();
    private volatile ServerSocket serverSocket;

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
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(config.bindAddress(), config.port()));
        executor.submit(this::acceptLoop);
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
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            listener.onError(e);
        }
        sessions.values().forEach(Session::close);
        executor.close();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                Session session = new Session(socket);
                sessions.put(session.id, session);
                executor.submit(session::readLoop);
            } catch (IOException e) {
                if (running.get()) {
                    listener.onError(e);
                }
            }
        }
    }

    private void handle(Session session, SmusMessage message) {
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

    private void handleLogon(Session session, SmusMessage message) {
        LValue content = LingoCodec.decode(message.content());
        Login login = Login.from(content).orElseThrow(() -> new IllegalArgumentException("Logon content must be a list or property list"));
        String existingSession = usersBySession.values().stream()
                .filter(user -> user.name().equalsIgnoreCase(login.userId()) && user.movie().equalsIgnoreCase(login.movieId()))
                .map(User::sessionId)
                .findFirst()
                .orElse(null);
        if (existingSession != null) {
            Session existing = sessions.get(existingSession);
            if (existing != null) {
                existing.close();
            }
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

    public record User(String name, String movie, Set<String> groups, String sessionId) {
        public User {
            groups = Set.copyOf(groups);
        }

        Set<String> groupsPlus(String group) {
            var copy = ConcurrentHashMap.<String>newKeySet();
            copy.addAll(groups);
            copy.add(group.startsWith("@") ? group : "@" + group);
            return copy;
        }

        Set<String> groupsMinus(String group) {
            String normalized = group.startsWith("@") ? group : "@" + group;
            var copy = ConcurrentHashMap.<String>newKeySet();
            groups.stream().filter(existing -> !existing.equalsIgnoreCase(normalized)).forEach(copy::add);
            return copy;
        }
    }

    private record Recipient(String name, Optional<String> movie) {
        static Recipient parse(String value) {
            String[] parts = (value + "@").split("@", -1);
            String name = parts.length > 0 ? parts[0] : "";
            Optional<String> movie = parts.length > 1 && !parts[1].isEmpty() ? Optional.of(parts[1]) : Optional.empty();
            if (name.isEmpty() && movie.isPresent()) {
                name = "@" + movie.orElseThrow();
                movie = Optional.empty();
            }
            return new Recipient(name, movie);
        }
    }

    private record Login(String movieId, String userId, int protocolMajor, int protocolMinor, int clientMajor, int clientMinor) {
        static Optional<Login> from(LValue value) {
            if (value instanceof LValue.ListValue list && list.values().size() >= 3
                    && list.values().get(0) instanceof LValue.StringValue movie
                    && list.values().get(1) instanceof LValue.StringValue user
                    && list.values().get(2) instanceof LValue.StringValue password) {
                int[] versions = versions(password.value());
                return Optional.of(new Login(movie.value(), user.value(), versions[0], versions[1], versions[2], versions[3]));
            }
            if (value instanceof LValue.PropertyList propertyList) {
                Map<String, LValue> values = propertyList.properties().stream()
                        .collect(java.util.stream.Collectors.toMap(p -> p.name().toLowerCase(Locale.ROOT), LValue.Property::value, (a, b) -> a));
                if (values.get("movieid") instanceof LValue.StringValue movie
                        && values.get("userid") instanceof LValue.StringValue user
                        && values.get("password") instanceof LValue.StringValue password) {
                    int[] versions = versions(password.value());
                    return Optional.of(new Login(movie.value(), user.value(), versions[0], versions[1], versions[2], versions[3]));
                }
            }
            return Optional.empty();
        }

        private static int[] versions(String password) {
            String[] parts = password.split("[,.]");
            if (parts.length == 4) {
                try {
                    return new int[] {
                            Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]), Integer.parseInt(parts[3])
                    };
                } catch (NumberFormatException ignored) {
                }
            }
            return new int[] {1, 0, 2, 142};
        }
    }

    private final class Session {
        private final Socket socket;
        private final String id = HexFormat.of().formatHex(UUID.randomUUID().toString().getBytes()).substring(0, 24);
        private final int[] lastMessages = new int[100];
        private volatile boolean authenticated;
        private volatile int protocolMajor;
        private volatile int protocolMinor;
        private volatile int clientMajor;
        private volatile int clientMinor;
        private volatile int messageHashIndex = -1;
        private volatile byte[] messageHash = new byte[0];

        private Session(Socket socket) {
            this.socket = socket;
        }

        private void readLoop() {
            try (socket) {
                while (running.get() && !socket.isClosed()) {
                    byte[] header = socket.getInputStream().readNBytes(SmusCodec.HEADER_SIZE);
                    if (header.length == 0) {
                        break;
                    }
                    if (header.length != SmusCodec.HEADER_SIZE) {
                        throw new EOFException("Incomplete SMUS header");
                    }
                    int frameLength = SmusCodec.frameLength(header);
                    if (frameLength > config.maxFrameBytes()) {
                        throw new IOException("SMUS frame exceeds maxFrameBytes");
                    }
                    byte[] body = socket.getInputStream().readNBytes(frameLength);
                    if (body.length != frameLength) {
                        throw new EOFException("Incomplete SMUS body");
                    }
                    handle(this, SmusCodec.unpack(body, authenticated ? null : loginKey));
                }
            } catch (Throwable e) {
                if (running.get()) {
                    listener.onError(e);
                }
            } finally {
                removeSession(this);
            }
        }

        private synchronized void send(SmusMessage message) {
            try {
                socket.getOutputStream().write(SmusCodec.pack(message));
                socket.getOutputStream().flush();
            } catch (IOException e) {
                close();
            }
        }

        private void close() {
            try {
                socket.close();
            } catch (IOException e) {
                listener.onError(e);
            }
        }

        private boolean checkOrder(int messageId) {
            int index = Math.floorMod(messageId, lastMessages.length);
            int previous = lastMessages[index];
            if (previous == messageId || previous > messageId) {
                return false;
            }
            lastMessages[index] = messageId;
            return true;
        }

        private int expectedHash(int order) {
            int block = Math.floorDiv(order, 16);
            int index = Math.floorMod(order, 16);
            if (messageHashIndex != block) {
                try {
                    MessageDigest md5 = MessageDigest.getInstance("MD5");
                    messageHash = md5.digest((config.encryptionKey() + id + block).getBytes(Binary.LINGO_CHARSET));
                    messageHashIndex = block;
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException(e);
                }
            }
            return messageHash[index] & 0xff;
        }
    }

    private void removeSession(Session session) {
        sessions.remove(session.id);
        User user = usersBySession.remove(session.id);
        if (user != null) {
            listener.onLogout(user);
        }
    }
}
