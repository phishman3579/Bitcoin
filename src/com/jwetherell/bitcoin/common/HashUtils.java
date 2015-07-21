package com.jwetherell.bitcoin.common;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;

public abstract class HashUtils {

    public static final byte[] calculateSha256(String text) {
        byte[] hash2;
        try {
            hash2 = calculateSha256(text.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return hash2;
    }

    public static final byte[] calculateSha256(byte[] utf8Bytes) {
        byte[] hash2;
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash1 = digest.digest(utf8Bytes);
            hash2 = digest.digest(hash1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return hash2;
    }

    public static final String bytesToHex(byte[] bytes) {
        final StringBuilder result = new StringBuilder();
        for (byte byt : bytes) 
            result.append(Integer.toString((byt & 0xff) + 0x100, 16).substring(1));
        return result.toString();
    }

}
