package io.github.bitpart.smus.protocol;

import java.util.List;
import java.util.Arrays;

/**
 * Director/Lingo values used by the Shockwave Multiuser Server protocol.
 */
public sealed interface LValue
        permits LValue.VoidValue, LValue.Symbol, LValue.StringValue, LValue.FloatValue,
        LValue.IntegerValue, LValue.Point, LValue.Rect, LValue.Color, LValue.Vector,
        LValue.Transform, LValue.DateValue, LValue.ListValue, LValue.PropertyList,
        LValue.Picture, LValue.Media, LValue.Raw {

    record VoidValue() implements LValue {
        public static final VoidValue INSTANCE = new VoidValue();
    }

    record Symbol(String value) implements LValue {}

    record StringValue(String value) implements LValue {}

    record FloatValue(double value) implements LValue {}

    record IntegerValue(int value) implements LValue {}

    record Point(LValue x, LValue y) implements LValue {}

    record Rect(LValue left, LValue top, LValue right, LValue bottom) implements LValue {}

    record Color(int red, int green, int blue) implements LValue {}

    record Vector(float x, float y, float z) implements LValue {}

    record Transform(float p0, float p1, float p2, float p3, float p4, float p5, float p6, float p7,
                     float p8, float p9, float pa, float pb, float pc, float pd, float pe, float pf)
            implements LValue {}

    record DateValue(int year, int month, int day, int seconds) implements LValue {}

    record ListValue(List<LValue> values) implements LValue {
        public ListValue {
            values = List.copyOf(values);
        }
    }

    record Property(String name, LValue value) {}

    record PropertyList(List<Property> properties) implements LValue {
        public PropertyList {
            properties = List.copyOf(properties);
        }
    }

    record Picture(byte[] bytes) implements LValue {
        public Picture {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Picture picture && Arrays.equals(bytes, picture.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }

    record Media(byte[] bytes) implements LValue {
        public Media {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Media media && Arrays.equals(bytes, media.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }

    record Raw(byte[] bytes) implements LValue {
        public Raw {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Raw raw && Arrays.equals(bytes, raw.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }
}
