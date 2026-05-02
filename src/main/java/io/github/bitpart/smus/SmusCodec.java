package io.github.bitpart.smus;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static io.github.bitpart.smus.Binary.*;

public final class SmusCodec {
    public static final int HEADER_SIZE = 6;
    private static final int HEADER_BYTE_0 = 114;
    private static final int HEADER_BYTE_1 = 0;

    private SmusCodec() {
    }

    public static byte[] pack(SmusMessage message) {
        return pack(message, null);
    }

    public static byte[] pack(SmusMessage message, String encryptionKey) {
        byte[] body = encodeBody(message, encryptionKey);
        var out = new ByteArrayOutputStream(HEADER_SIZE + body.length);
        writeByte(out, HEADER_BYTE_0);
        writeByte(out, HEADER_BYTE_1);
        writeInt(out, body.length);
        out.writeBytes(body);
        return out.toByteArray();
    }

    public static SmusMessage unpack(byte[] body) {
        return unpack(body, null);
    }

    public static SmusMessage unpack(byte[] body, SmusBlowfish.KeySchedule encryptedContentKey) {
        var in = reader(body);
        int errorCode = readInt(in);
        int timeStamp = readInt(in);
        String subject = LingoCodec.readString(in);
        String sender = LingoCodec.readString(in);
        int recipientCount = readInt(in);
        var recipients = new ArrayList<String>(recipientCount);
        for (int i = 0; i < recipientCount; i++) {
            recipients.add(LingoCodec.readString(in));
        }
        byte[] content = new byte[in.remaining()];
        in.get(content);
        if (encryptedContentKey != null) {
            content = SmusBlowfish.transform(encryptedContentKey, content);
        }
        return new SmusMessage(errorCode, timeStamp, subject, sender, recipients, content);
    }

    public static int frameLength(byte[] header) {
        if (header.length != HEADER_SIZE) {
            throw new IllegalArgumentException("SMUS header must be 6 bytes");
        }
        if ((header[0] & 0xff) != HEADER_BYTE_0 || (header[1] & 0xff) != HEADER_BYTE_1) {
            throw new IllegalArgumentException("Invalid SMUS header");
        }
        return ByteBuffer.wrap(header, 2, 4).getInt();
    }

    private static byte[] encodeBody(SmusMessage message, String encryptionKey) {
        var out = new ByteArrayOutputStream();
        writeInt(out, message.errorCode());
        writeInt(out, message.timeStamp());
        LingoCodec.writeString(out, message.subject());
        LingoCodec.writeString(out, message.sender());
        writeInt(out, message.recipients().size());
        message.recipients().forEach(recipient -> LingoCodec.writeString(out, recipient));
        byte[] content = message.content();
        out.writeBytes(encryptionKey == null ? content : SmusBlowfish.transform(encryptionKey, content));
        return out.toByteArray();
    }
}
