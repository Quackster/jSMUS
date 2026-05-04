package io.github.bitpart.smus;

import io.github.bitpart.smus.crypto.SmusBlowfish;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SmusBlowfishTest {
    private static final byte[] KEY = {
            73, 80, 65, 100, 100, 114, 101, 115, 115, 32,
            114, 101, 115, 111, 108, 117, 116, 105, 111, 110
    };

    @Test
    void decryptsKnownKeyBlock() {
        var schedule = SmusBlowfish.keySchedule(KEY);

        assertEquals(-2567789888536546563L, SmusBlowfish.decryptBlock(schedule, 203447117607259685L));
    }

    @Test
    void transformsKnownServerPayload() {
        var schedule = SmusBlowfish.keySchedule(KEY);
        byte[] buffer = {
                (byte) 140, (byte) 176, 97, (byte) 202, 17, 83, (byte) 160, 87,
                (byte) 248, 108, (byte) 216, (byte) 200, 10, 79, (byte) 147, 72,
                99, (byte) 217, 105, 108, 74, 27, 76, 59,
                (byte) 165, (byte) 198, 52, 53, (byte) 211, (byte) 218, 65, 29,
                90, 8, (byte) 128, 113, (byte) 171, 83, (byte) 131, 9
        };

        SmusBlowfish.transformInPlace(schedule, buffer);

        assertTrue(buffer[0] == 0 && buffer[1] == 7 && buffer[2] == 0 && buffer[3] == 0);
        assertTrue(buffer[4] == 0 && buffer[5] == 3 && buffer[6] == 0 && buffer[7] == 3);
        assertTrue(buffer[37] == 111 && buffer[38] == 114 && buffer[39] == 100);
    }
}
