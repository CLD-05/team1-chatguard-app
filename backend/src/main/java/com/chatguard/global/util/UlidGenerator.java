package com.chatguard.global.util;

import java.time.Instant;
import java.util.Random;

public class UlidGenerator {

    private static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final Random RANDOM = new Random();

    public static String generate() {
        long now = Instant.now().toEpochMilli();
        char[] ulid = new char[26];
        for (int i = 9; i >= 0; i--) {
            ulid[i] = ENCODING[(int) (now & 0x1F)];
            now >>= 5;
        }
        for (int i = 10; i < 26; i++) {
            ulid[i] = ENCODING[RANDOM.nextInt(32)];
        }
        return new String(ulid);
    }
}
