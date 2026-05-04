package io.github.bitpart.smus.server;

import java.util.Optional;

record Recipient(String name, Optional<String> movie) {
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
