package com.chatguard.global.util;

import java.security.SecureRandom;
import java.time.Instant;

public class UlidGenerator {
    private static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private UlidGenerator() {
    }

    public static String generate() {
        byte[] bytes = new byte[16];
        long time = Instant.now().toEpochMilli();

        bytes[0] = (byte) (time >>> 40);
        bytes[1] = (byte) (time >>> 32);
        bytes[2] = (byte) (time >>> 24);
        bytes[3] = (byte) (time >>> 16);
        bytes[4] = (byte) (time >>> 8);
        bytes[5] = (byte) time;

        byte[] randomBytes = new byte[10];
        RANDOM.nextBytes(randomBytes);
        System.arraycopy(randomBytes, 0, bytes, 6, 10);

        return encode(bytes);
    }

    private static String encode(byte[] bytes) {
        char[] chars = new char[26];
        int bitBuffer = 0;
        int bitBufferLength = 0;
        int charIndex = 0;

        for (byte b : bytes) {
            bitBuffer = (bitBuffer << 8) | (b & 0xff);
            bitBufferLength += 8;
            while (bitBufferLength >= 5 && charIndex < chars.length) {
                int index = (bitBuffer >> (bitBufferLength - 5)) & 0x1f;
                chars[charIndex++] = ENCODING[index];
                bitBufferLength -= 5;
            }
        }

        if (charIndex < chars.length) {
            chars[charIndex] = ENCODING[(bitBuffer << (5 - bitBufferLength)) & 0x1f];
        }

        return new String(chars);
    }
}
