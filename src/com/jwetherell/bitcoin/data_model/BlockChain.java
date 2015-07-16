package com.jwetherell.bitcoin.data_model;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BlockChain {

    private final List<Coin>                transactions    = new CopyOnWriteArrayList<Coin>();

    // Generate the genesis
    private byte[]                          hash;
    {
        final byte[] h = "Genesis hash".getBytes();

        final Coin g = (new Coin("me","you","Genesis.",1));
        final ByteBuffer buffer = ByteBuffer.allocate(g.getBufferLength());
        g.toBuffer(buffer);
        final byte[] c = buffer.array();

        final String string = new String(h) + new String(c);
        hash = calculateSha256(string);
    }

    public BlockChain() { }

    public synchronized Transaction getNextTransaction(Coin coin) {
        final ByteBuffer buffer = ByteBuffer.allocate(coin.getBufferLength());
        coin.toBuffer(buffer);
        final byte[] bytes = buffer.array();

        final byte[] nextHash = getNextHash(hash, bytes);
        return (new Transaction(nextHash, coin));
    }

    public synchronized void addTransaction(Transaction trans) {
        final Coin coin = trans.getCoin();

        final ByteBuffer buffer = ByteBuffer.allocate(coin.getBufferLength());
        coin.toBuffer(buffer);
        final byte[] bytes = buffer.array();
        final byte[] nextHash = getNextHash(hash, bytes);

        final byte[] incomingHash = trans.getHash();
        if (!(Arrays.equals(incomingHash, nextHash))) {
            System.err.println("Invalid hash on transaction.");
            return;
        }

        hash = nextHash;
        transactions.add(coin);
    }

    public synchronized long getBalance(String name) {
        long result = 0;
        for (Coin c : transactions) {
            if (name.equals(c.from))
                result -= c.value;
            else if (name.equals(c.to))
                result += c.value;
        }
        return result;
    }

    public static final byte[] getNextHash(byte[] hash, byte[] bytes) {
        final String string = new String(hash) + new String(bytes);
        final byte[] output = calculateSha256(string);
        return output;
    }

    public static final byte[] calculateSha256(String text) {
        byte[] hash2;
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash1 = digest.digest(text.getBytes("UTF-8"));
            hash2 = digest.digest(hash1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return hash2;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("Transactions={").append("\n");
        for (Coin c : transactions) {
            builder.append('\t').append(c.value).append(" from ").append(c.from).append(" to ").append(c.to).append("\n");
        }
        builder.append("}");

        return builder.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BlockChain))
            return false;
        BlockChain b = (BlockChain) o;
        for (Coin c : transactions) {
            if (!(b.transactions.contains(c)))
                return false;
        }
        return true;
    }
}
