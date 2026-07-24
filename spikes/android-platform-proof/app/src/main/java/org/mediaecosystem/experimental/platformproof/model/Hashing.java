package org.mediaecosystem.experimental.platformproof.model;

public final class Hashing {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Hashing() {}

    public static String hex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            result[index * 2] = HEX[value >>> 4];
            result[index * 2 + 1] = HEX[value & 0x0f];
        }
        return new String(result);
    }
}
