package com.jwetherell.bitcoin;

import com.jwetherell.bitcoin.common.HashUtils;

public class ProofOfWork {

    private static final boolean DEBUG = Boolean.getBoolean("debug");

    /** 
     * Given the sha256 hash, what number can we append to the hash which'll create a hash
     * which has a leading 'numberOfZerosInPrefix' number of zeros.
     * 
     * e.g.
     * if numberOfZerosInPrefix == 3
     * input = 7982970534e089b839957b7e174725ce1878731ed6d700766e59cb16f1c25e27
     * solution = 0000fedb24e31adb71559d43a9a3ddc8be7606fa6befe3260b3eee2cf2aeb642
     * output = 21080
     * 
     **/
    public static final long solve(byte[] sha256, long numberOfZerosInPrefix) {
        final StringBuilder builder = new StringBuilder();

        // Create the prefix.
        for (int i=0; i<numberOfZerosInPrefix; i++)
            builder.append("0");
        final String prefix = builder.toString();

        // Solve
        final String h = HashUtils.bytesToHex(sha256);
        long x = 0;
        String r = "";
        while (x < Long.MAX_VALUE) {
            builder.setLength(0);
            builder.append(h).append(x);
            final byte[] result = HashUtils.calculateSha256(builder.toString());
            final String hex = HashUtils.bytesToHex(result);
            r = hex;
            // Keep looping until we calculate 'hex' which starts 'prefix'
            if (r.startsWith(prefix))
                break;
            x++;
        }

        if (DEBUG)
            System.out.println("r="+r+" has "+numberOfZerosInPrefix+" number of zeros as a prefix.");

        return x;
    }

    /** Does the given nonce create a hash which starts with 'numberOfZerosInPrefix' number of zeros **/
    public static final boolean check(byte[] sha256, long nonce, long numberOfZerosInPrefix) {
        final StringBuilder builder = new StringBuilder();

        // Create the prefix.
        for (int i=0; i<numberOfZerosInPrefix; i++)
            builder.append("0");
        final String prefix = builder.toString();

        // Solve
        final String h = HashUtils.bytesToHex(sha256);
        final long x = nonce;
        builder.setLength(0);
        builder.append(h).append(x);
        final byte[] result = HashUtils.calculateSha256(builder.toString());
        final String hex = HashUtils.bytesToHex(result);

        return hex.startsWith(prefix);
    }
}
