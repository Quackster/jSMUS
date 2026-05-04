package io.github.bitpart.smus.crypto;

import java.util.Arrays;

import java.nio.charset.StandardCharsets;

public final class SmusBlowfish {
    private static final int PBOX_ENTRIES = 18;
    private static final int SBOX_ENTRIES = 256;

    private SmusBlowfish() {
    }

    public static KeySchedule keySchedule(String key) {
        return keySchedule(key.getBytes(StandardCharsets.ISO_8859_1));
    }

    public static KeySchedule keySchedule(byte[] key) {
        if (key.length == 0) {
            throw new IllegalArgumentException("Empty Blowfish key");
        }
        int[] p = BlowfishTables.P.clone();
        int[] s1 = BlowfishTables.S[0].clone();
        int[] s2 = BlowfishTables.S[1].clone();
        int[] s3 = BlowfishTables.S[2].clone();
        int[] s4 = BlowfishTables.S[3].clone();

        int keyPos = 0;
        int build = 0;
        for (int i = 0; i < PBOX_ENTRIES; i++) {
            for (int j = 0; j < 4; j++) {
                build = (build << 8) | (key[keyPos] & 0xff);
                keyPos++;
                if (keyPos == key.length) {
                    keyPos = 0;
                }
            }
            p[i] ^= build;
        }

        long zero = 0;
        zero = fill(p, PBOX_ENTRIES, zero, p, s1, s2, s3, s4);
        zero = fill(s1, SBOX_ENTRIES, zero, p, s1, s2, s3, s4);
        zero = fill(s2, SBOX_ENTRIES, zero, p, s1, s2, s3, s4);
        zero = fill(s3, SBOX_ENTRIES, zero, p, s1, s2, s3, s4);
        fill(s4, SBOX_ENTRIES, zero, p, s1, s2, s3, s4);
        return new KeySchedule(p, s1, s2, s3, s4);
    }

    public static byte[] transform(String key, byte[] data) {
        return transform(keySchedule(key), data);
    }

    public static byte[] transform(KeySchedule schedule, byte[] data) {
        int length = data.length;
        int paddedLength = ((length - 1) & ~7) + 8;
        byte[] buffer = Arrays.copyOf(data, paddedLength);
        Arrays.fill(buffer, length, paddedLength, (byte) 0x20);
        transformInPlace(schedule, buffer);
        return Arrays.copyOf(buffer, length);
    }

    public static void transformInPlace(KeySchedule schedule, byte[] buffer) {
        long cbcIv = 0;
        for (int offset = 0; offset < buffer.length; offset += 8) {
            cbcIv = decryptBlock(schedule, cbcIv);
            long block = readLong(buffer, offset);
            writeLong(buffer, offset, cbcIv ^ block);
        }
    }

    public static long decryptBlock(KeySchedule schedule, long cipherBlock) {
        return decryptBlock(schedule.p, schedule.s1, schedule.s2, schedule.s3, schedule.s4, cipherBlock);
    }

    private static long fill(int[] target, int count, long zero, int[] p, int[] s1, int[] s2, int[] s3, int[] s4) {
        for (int i = 0; i < count; i += 2) {
            zero = decryptBlock(p, s1, s2, s3, s4, zero);
            target[i] = (int) (zero >>> 32);
            target[i + 1] = (int) zero;
        }
        return zero;
    }

    private static long decryptBlock(int[] p, int[] s1, int[] s2, int[] s3, int[] s4, long cipherBlock) {
        int hi = (int) (cipherBlock >>> 32);
        int lo = (int) cipherBlock;

        hi ^= p[17];
        lo ^= f(hi, s1, s2, s3, s4) ^ p[16];
        hi ^= f(lo, s1, s2, s3, s4) ^ p[15];
        lo ^= f(hi, s1, s2, s3, s4) ^ p[14];
        hi ^= f(lo, s1, s2, s3, s4) ^ p[13];
        lo ^= f(hi, s1, s2, s3, s4) ^ p[12];
        hi ^= f(lo, s1, s2, s3, s4) ^ p[11];
        lo ^= f(hi, s1, s2, s3, s4) ^ p[10];
        hi ^= f(lo, s1, s2, s3, s4) ^ p[9];
        lo ^= f(hi, s1, s2, s3, s4) ^ p[8];
        hi ^= f(lo, s1, s2, s3, s4) ^ p[7];
        lo ^= f(hi, s1, s2, s3, s4) ^ p[6];
        hi ^= f(lo, s1, s2, s3, s4) ^ p[5];
        lo ^= f(hi, s1, s2, s3, s4) ^ p[4];
        hi ^= f(lo, s1, s2, s3, s4) ^ p[3];
        lo ^= f(hi, s1, s2, s3, s4) ^ p[2];
        hi ^= f(lo, s1, s2, s3, s4);

        int low = hi ^ p[1];
        int high = lo ^ p[0];
        return ((long) high << 32) | (low & 0xffffffffL);
    }

    private static int f(int x, int[] s1, int[] s2, int[] s3, int[] s4) {
        return ((s1[(x >>> 24) & 0xff] + s2[(x >>> 16) & 0xff]) ^ s3[(x >>> 8) & 0xff]) + s4[x & 0xff];
    }

    private static long readLong(byte[] buffer, int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (buffer[offset + i] & 0xffL);
        }
        return value;
    }

    private static void writeLong(byte[] buffer, int offset, long value) {
        for (int i = 7; i >= 0; i--) {
            buffer[offset + i] = (byte) value;
            value >>>= 8;
        }
    }

    public record KeySchedule(int[] p, int[] s1, int[] s2, int[] s3, int[] s4) {
        public KeySchedule {
            p = p.clone();
            s1 = s1.clone();
            s2 = s2.clone();
            s3 = s3.clone();
            s4 = s4.clone();
        }

        @Override
        public int[] p() {
            return p.clone();
        }

        @Override
        public int[] s1() {
            return s1.clone();
        }

        @Override
        public int[] s2() {
            return s2.clone();
        }

        @Override
        public int[] s3() {
            return s3.clone();
        }

        @Override
        public int[] s4() {
            return s4.clone();
        }
    }
}
