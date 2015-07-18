package com.jwetherell.bitcoin.data_model;

public class ProofOfWork {

    private static final boolean DEBUG = Boolean.getBoolean("debug");

    public static final long solve(byte[] sha256, long numberOfZerosInPrefix) {
        final StringBuilder builder = new StringBuilder();

        // Create the prefix.
        for (int i=0; i<numberOfZerosInPrefix; i++)
            builder.append("0");
        final String prefix = builder.toString();

        // Solve
        final String h = BlockChain.bytesToHex(sha256);
        long x = 0;
        String r = "";
        while (x < Long.MAX_VALUE) {
            builder.setLength(0);
            builder.append(h).append(x);
            final byte[] result = BlockChain.calculateSha256(builder.toString());
            final String hex = BlockChain.bytesToHex(result);
            r = hex;
            if (r.startsWith(prefix))
                break;
            x++;
        }

        if (DEBUG)
            System.out.println("r="+r+" has "+numberOfZerosInPrefix+" number of zeros as a prefix.");

        return x;
    }

    public static final boolean check(byte[] sha256, long nonce, long numberOfZerosInPrefix) {
        final StringBuilder builder = new StringBuilder();

        // Create the prefix.
        for (int i=0; i<numberOfZerosInPrefix; i++)
            builder.append("0");
        final String prefix = builder.toString();

        // Solve
        final String h = BlockChain.bytesToHex(sha256);
        final long x = nonce;
        builder.setLength(0);
        builder.append(h).append(x);
        final byte[] result = BlockChain.calculateSha256(builder.toString());
        final String hex = BlockChain.bytesToHex(result);

        return hex.startsWith(prefix);
    }
}
