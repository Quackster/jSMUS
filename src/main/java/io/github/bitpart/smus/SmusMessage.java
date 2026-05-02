package io.github.bitpart.smus;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record SmusMessage(
        int errorCode,
        int timeStamp,
        String subject,
        String sender,
        List<String> recipients,
        byte[] content) {

    public SmusMessage {
        recipients = List.copyOf(recipients);
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    public static SmusMessage of(String sender, List<String> recipients, String subject, byte[] content) {
        return new SmusMessage(0, 0, subject, sender, recipients, content);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SmusMessage message
                && errorCode == message.errorCode
                && timeStamp == message.timeStamp
                && Objects.equals(subject, message.subject)
                && Objects.equals(sender, message.sender)
                && Objects.equals(recipients, message.recipients)
                && Arrays.equals(content, message.content);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(errorCode, timeStamp, subject, sender, recipients);
        result = 31 * result + Arrays.hashCode(content);
        return result;
    }
}
