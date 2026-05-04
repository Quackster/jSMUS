package io.github.bitpart.smus.server;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
