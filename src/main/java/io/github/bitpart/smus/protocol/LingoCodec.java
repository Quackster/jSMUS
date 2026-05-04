package io.github.bitpart.smus.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static io.github.bitpart.smus.protocol.Binary.*;

public final class LingoCodec {
    private static final short VOID = 0;
    private static final short INTEGER = 1;
    private static final short SYMBOL = 2;
    private static final short STRING = 3;
    private static final short PICTURE = 5;
    private static final short FLOAT = 6;
    private static final short LIST = 7;
    private static final short POINT = 8;
    private static final short RECT = 9;
    private static final short PROP_LIST = 10;
    private static final short COLOR = 18;
    private static final short DATE = 19;
    private static final short MEDIA = 20;
    private static final short VECTOR = 22;
    private static final short TRANSFORM = 23;

    private LingoCodec() {
    }

    public static byte[] encode(LValue value) {
        var out = new ByteArrayOutputStream();
        writeValue(out, value);
        return out.toByteArray();
    }

    public static LValue decode(byte[] bytes) {
        return readValue(reader(bytes));
    }

    public static LValue.ListValue stringList(List<String> values) {
        return new LValue.ListValue(values.stream().map(LValue.StringValue::new).map(LValue.class::cast).toList());
    }

    static void writeString(ByteArrayOutputStream out, String value) {
        writeChunk(out, value.getBytes(LINGO_CHARSET));
    }

    static String readString(ByteBuffer in) {
        return new String(readChunk(in), LINGO_CHARSET);
    }

    private static void writeChunk(ByteArrayOutputStream out, byte[] bytes) {
        writeInt(out, bytes.length);
        out.writeBytes(bytes);
        if (bytes.length % 2 == 1) {
            writeByte(out, 0);
        }
    }

    private static byte[] readChunk(ByteBuffer in) {
        int length = readInt(in);
        byte[] bytes = new byte[length];
        in.get(bytes);
        if (length % 2 == 1) {
            in.get();
        }
        return bytes;
    }

    private static void writeValue(ByteArrayOutputStream out, LValue value) {
        switch (value) {
            case LValue.VoidValue ignored -> writeShort(out, VOID);
            case LValue.IntegerValue v -> {
                writeShort(out, INTEGER);
                writeInt(out, v.value());
            }
            case LValue.FloatValue v -> {
                writeShort(out, FLOAT);
                writeDouble(out, v.value());
            }
            case LValue.Symbol v -> {
                writeShort(out, SYMBOL);
                writeString(out, v.value());
            }
            case LValue.StringValue v -> {
                writeShort(out, STRING);
                writeString(out, v.value());
            }
            case LValue.ListValue v -> {
                writeShort(out, LIST);
                writeInt(out, v.values().size());
                v.values().forEach(element -> writeValue(out, element));
            }
            case LValue.Point v -> {
                writeShort(out, POINT);
                writeValue(out, v.x());
                writeValue(out, v.y());
            }
            case LValue.Rect v -> {
                writeShort(out, RECT);
                writeValue(out, v.left());
                writeValue(out, v.top());
                writeValue(out, v.right());
                writeValue(out, v.bottom());
            }
            case LValue.Vector v -> {
                writeShort(out, VECTOR);
                writeFloat(out, v.x());
                writeFloat(out, v.y());
                writeFloat(out, v.z());
            }
            case LValue.Transform v -> {
                writeShort(out, TRANSFORM);
                writeFloat(out, v.p0()); writeFloat(out, v.p1()); writeFloat(out, v.p2()); writeFloat(out, v.p3());
                writeFloat(out, v.p4()); writeFloat(out, v.p5()); writeFloat(out, v.p6()); writeFloat(out, v.p7());
                writeFloat(out, v.p8()); writeFloat(out, v.p9()); writeFloat(out, v.pa()); writeFloat(out, v.pb());
                writeFloat(out, v.pc()); writeFloat(out, v.pd()); writeFloat(out, v.pe()); writeFloat(out, v.pf());
            }
            case LValue.PropertyList v -> {
                writeShort(out, PROP_LIST);
                writeInt(out, v.properties().size());
                for (var property : v.properties()) {
                    writeValue(out, new LValue.Symbol(property.name()));
                    writeValue(out, property.value());
                }
            }
            case LValue.Color v -> {
                writeShort(out, COLOR);
                writeByte(out, 1);
                writeByte(out, v.red());
                writeByte(out, v.green());
                writeByte(out, v.blue());
            }
            case LValue.DateValue v -> {
                writeShort(out, DATE);
                long unixTime = unixTime(v.year(), v.month(), v.day(), v.seconds());
                writeInt(out, (int) unixTime);
                writeInt(out, (int) (unixTime >>> 32));
            }
            case LValue.Picture v -> {
                writeShort(out, PICTURE);
                writeChunk(out, v.bytes());
            }
            case LValue.Media v -> {
                writeShort(out, MEDIA);
                writeChunk(out, v.bytes());
            }
            case LValue.Raw v -> out.writeBytes(v.bytes());
        }
    }

    private static LValue readValue(ByteBuffer in) {
        short type = readShort(in);
        return switch (type) {
            case VOID -> LValue.VoidValue.INSTANCE;
            case INTEGER -> new LValue.IntegerValue(readInt(in));
            case FLOAT -> new LValue.FloatValue(readDouble(in));
            case SYMBOL -> new LValue.Symbol(readString(in));
            case STRING -> new LValue.StringValue(readString(in));
            case LIST -> new LValue.ListValue(readList(in));
            case POINT -> new LValue.Point(readValue(in), readValue(in));
            case RECT -> new LValue.Rect(readValue(in), readValue(in), readValue(in), readValue(in));
            case VECTOR -> new LValue.Vector(readFloat(in), readFloat(in), readFloat(in));
            case TRANSFORM -> new LValue.Transform(
                    readFloat(in), readFloat(in), readFloat(in), readFloat(in),
                    readFloat(in), readFloat(in), readFloat(in), readFloat(in),
                    readFloat(in), readFloat(in), readFloat(in), readFloat(in),
                    readFloat(in), readFloat(in), readFloat(in), readFloat(in));
            case PROP_LIST -> readPropertyList(in);
            case COLOR -> {
                readUnsignedByte(in);
                yield new LValue.Color(readUnsignedByte(in), readUnsignedByte(in), readUnsignedByte(in));
            }
            case DATE -> {
                int lo = readInt(in);
                int hi = readInt(in);
                yield dateValue((((long) hi) << 32) | (lo & 0xffffffffL));
            }
            case PICTURE -> new LValue.Picture(readChunk(in));
            case MEDIA -> new LValue.Media(readChunk(in));
            default -> {
                var bytes = new byte[in.remaining() + 2];
                bytes[0] = (byte) (type >>> 8);
                bytes[1] = (byte) type;
                in.get(bytes, 2, in.remaining());
                yield new LValue.Raw(bytes);
            }
        };
    }

    private static List<LValue> readList(ByteBuffer in) {
        int count = readInt(in);
        var values = new ArrayList<LValue>(count);
        for (int i = 0; i < count; i++) {
            values.add(readValue(in));
        }
        return values;
    }

    private static LValue.PropertyList readPropertyList(ByteBuffer in) {
        int count = readInt(in);
        var properties = new ArrayList<LValue.Property>(count);
        for (int i = 0; i < count; i++) {
            LValue key = readValue(in);
            if (!(key instanceof LValue.Symbol symbol)) {
                throw new IllegalArgumentException("Property list key was not a symbol");
            }
            properties.add(new LValue.Property(symbol.value(), readValue(in)));
        }
        return new LValue.PropertyList(properties);
    }

    private static long unixTime(int year, int month, int day, int seconds) {
        int shiftedMonth = (month + 9) % 12;
        int shiftedYear = year - shiftedMonth / 10;
        long days = 365L * shiftedYear + shiftedYear / 4 - shiftedYear / 100 + shiftedYear / 400
                + (shiftedMonth * 306L + 5) / 10 + (day - 1) - 719468;
        return days * 86_400L + seconds;
    }

    private static LValue.DateValue dateValue(long unixTime) {
        long days = Math.floorDiv(unixTime, 86_400L);
        int seconds = (int) Math.floorMod(unixTime, 86_400L);
        long g = 719468L + days;
        long yPrime = (10000L * g + 14780L) / 3652425L;
        long dddPrime = g - (365L * yPrime + yPrime / 4L - yPrime / 100L + yPrime / 400L);
        int year;
        int ddd;
        if (dddPrime < 0L) {
            long y = yPrime - 1L;
            year = (int) y;
            ddd = (int) (g - (365L * y + y / 4L - y / 100L + y / 400L));
        } else {
            year = (int) yPrime;
            ddd = (int) dddPrime;
        }
        int mi = (100 * ddd + 52) / 3060;
        int month = (mi + 2) % 12 + 1;
        year = year + (mi + 2) / 12;
        int day = ddd - (mi * 306 + 5) / 10 + 1;
        return new LValue.DateValue(year, month, day, seconds);
    }
}
