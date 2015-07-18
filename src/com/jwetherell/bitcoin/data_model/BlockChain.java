package com.jwetherell.bitcoin.data_model;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BlockChain {

    public static enum HashStatus { BAD_KEY, BAD_HASH, SUCCESS };

    private final Queue<Block>               transactions    = new ConcurrentLinkedQueue<Block>();

    // Generate the genesis
    private byte[]                          hash;
    {
        final byte[] h = "Genesis hash".getBytes();

        final Block g = (new Block("me","you","Genesis.",1));
        final ByteBuffer buffer = ByteBuffer.allocate(g.getBufferLength());
        g.toBuffer(buffer);
        final byte[] c = buffer.array();

        final String string = new String(h) + new String(c);
        hash = calculateSha256(string);
    }

    public BlockChain() { }

    // synchronized to protected hash from changing while processing
    public synchronized Transaction getNextTransaction(String from, Block block) {
        final ByteBuffer buffer = ByteBuffer.allocate(block.getBufferLength());
        block.toBuffer(buffer);
        final byte[] bytes = buffer.array();

        final byte[] nextHash = getNextHash(hash, bytes);
        return (new Transaction(from, hash, nextHash, block));
    }

    // synchronized to protected hash from changing while processing
    public synchronized HashStatus checkTransaction(Transaction trans) {
        final Block block = trans.block;
        final ByteBuffer buffer = ByteBuffer.allocate(block.getBufferLength());
        block.toBuffer(buffer);
        final byte[] bytes = buffer.array();
        final byte[] nextHash = getNextHash(hash, bytes);

        final byte[] incomingHash = trans.hash;
        if (!(Arrays.equals(incomingHash, nextHash))) {
            System.err.println("Invalid hash on transaction.");
            return HashStatus.BAD_HASH;
        }

        return HashStatus.SUCCESS;
    }

    // synchronized to protected hash/transactions from changing while processing
    public synchronized HashStatus addTransaction(Transaction trans) {
        // Already processed this block.
        final Block block = trans.block;
        if (transactions.contains(block))
            return HashStatus.SUCCESS;

        final HashStatus status = checkTransaction(trans);
        if (status != HashStatus.SUCCESS)
            return status;

        final ByteBuffer buffer = ByteBuffer.allocate(block.getBufferLength());
        block.toBuffer(buffer);
        final byte[] bytes = buffer.array();
        final byte[] nextHash = getNextHash(hash, bytes);

        hash = nextHash;
        transactions.add(block);
        return HashStatus.SUCCESS;
    }

    // synchronized to protected transactions from changing while processing
    public synchronized long getBalance(String name) {
        long result = 0;
        for (Block c : transactions) {
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

    public static final String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte byt : bytes) 
            result.append(Integer.toString((byt & 0xff) + 0x100, 16).substring(1));
        return result.toString();
    }

    /**
     * {@inheritDoc}
     * 
     * synchronized to protected transactions from changing while processing
     */
    @Override
    public synchronized boolean equals(Object o) {
        if (!(o instanceof BlockChain))
            return false;
        BlockChain b = (BlockChain) o;
        for (Block c : transactions) {
            if (!(b.transactions.contains(c)))
                return false;
        }
        return true;
    }
    /**
     * {@inheritDoc}
     * 
     * synchronized to protected transactions from changing while processing
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("hash=[").append(BlockChain.bytesToHex(hash)).append("]\n");
        builder.append("Transactions={").append("\n");
        for (Block c : transactions)
            builder.append('\t').append(c.value).append(" from ").append(c.from).append(" to ").append(c.to).append("\n");
        builder.append("}");
        return builder.toString();
    }
}
