package com.chatguard.global.util;

import java.security.SecureRandom;
import java.time.Instant;

public final class UlidGenerator {
    private static final char[] CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ULID_LENGTH = 26;
    private static final int RANDOM_PART_LENGTH = 16;

    private UlidGenerator() {
    }

    public static String generate() {
        char[] ulid = new char[ULID_LENGTH];
        long timestamp = Instant.now().toEpochMilli();

        encodeTimestamp(timestamp, ulid);
        encodeRandom(ulid);

        return new String(ulid);
    }

    private static void encodeTimestamp(long timestamp, char[] target) {
        for (int i = 9; i >= 0; i--) {
            target[i] = CROCKFORD_BASE32[(int) (timestamp & 0x1f)];
            timestamp >>>= 5;
        }
    }

    private static void encodeRandom(char[] target) {
        byte[] randomBytes = new byte[10];
        RANDOM.nextBytes(randomBytes);

        int bitBuffer = 0;
        int bitBufferLength = 0;
        int targetIndex = ULID_LENGTH - RANDOM_PART_LENGTH;

        for (byte randomByte : randomBytes) {
            bitBuffer = (bitBuffer << 8) | (randomByte & 0xff);
            bitBufferLength += 8;

            while (bitBufferLength >= 5 && targetIndex < ULID_LENGTH) {
                int index = (bitBuffer >> (bitBufferLength - 5)) & 0x1f;
                target[targetIndex++] = CROCKFORD_BASE32[index];
                bitBufferLength -= 5;
            }
        }
    }
}
