package io.github.bitpart.smus.server;

import io.github.bitpart.smus.protocol.LValue;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

record Login(String movieId, String userId, int protocolMajor, int protocolMinor, int clientMajor, int clientMinor) {
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
