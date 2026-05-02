package io.github.bitpart.smus;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class Binary {
    static final Charset LINGO_CHARSET = StandardCharsets.ISO_8859_1;

    private Binary() {
    }

    static void writeByte(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
    }

    static void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 0xff);
        out.write(value & 0xff);
    }

    static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write(value & 0xff);
    }

    static void writeFloat(ByteArrayOutputStream out, float value) {
        writeInt(out, Float.floatToRawIntBits(value));
    }

    static void writeDouble(ByteArrayOutputStream out, double value) {
        long bits = Double.doubleToRawLongBits(value);
        writeInt(out, (int) (bits >>> 32));
        writeInt(out, (int) bits);
    }

    static int readUnsignedByte(ByteBuffer in) {
        return in.get() & 0xff;
    }

    static short readShort(ByteBuffer in) {
        return in.getShort();
    }

    static int readInt(ByteBuffer in) {
        return in.getInt();
    }

    static float readFloat(ByteBuffer in) {
        return in.getFloat();
    }

    static double readDouble(ByteBuffer in) {
        return in.getDouble();
    }

    static ByteBuffer reader(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
    }
}
