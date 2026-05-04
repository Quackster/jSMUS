package io.github.bitpart.smus;

import io.github.bitpart.smus.protocol.LValue;
import io.github.bitpart.smus.protocol.LingoCodec;
import io.github.bitpart.smus.protocol.SmusCodec;
import io.github.bitpart.smus.protocol.SmusMessage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SmusCodecTest {
    @Test
    void roundTripsLingoValues() {
        LValue value = new LValue.PropertyList(List.of(
                new LValue.Property("move", new LValue.ListValue(List.of(
                        new LValue.Rect(new LValue.IntegerValue(2), new LValue.FloatValue(-2879.54),
                                new LValue.IntegerValue(-7984), new LValue.FloatValue(1.1)),
                        new LValue.DateValue(2012, 2, 28, 34786)))),
                new LValue.Property("object", new LValue.StringValue("Queen")),
                new LValue.Property("Color", new LValue.Color(255, 0, 55)),
                new LValue.Property("img", new LValue.Picture(new byte[] {12, 24, 24}))
        ));

        assertEquals(value, LingoCodec.decode(LingoCodec.encode(value)));
    }

    @Test
    void roundTripsSmusMessages() {
        LValue content = new LValue.PropertyList(List.of(
                new LValue.Property("object", new LValue.StringValue("Queen")),
                new LValue.Property("Color", new LValue.Color(255, 0, 55))
        ));
        var message = new SmusMessage(
                -1024,
                2015,
                "NewMessage",
                "TheSender",
                List.of("recipient1", "recipient2", "@AllUsers"),
                LingoCodec.encode(content));

        byte[] frame = SmusCodec.pack(message);
        byte[] body = Arrays.copyOfRange(frame, SmusCodec.HEADER_SIZE, frame.length);

        assertEquals(message, SmusCodec.unpack(body));
    }
}
