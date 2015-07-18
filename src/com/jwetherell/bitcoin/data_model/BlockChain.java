package com.jwetherell.bitcoin.data_model;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BlockChain {

    public static final String              NO_ONE              = "no one";
    public static final String              GENESIS_NAME        = "genesis";

    public static enum                      BlockChainStatus    { NO_PUBLIC_KEY, BAD_SIGNATURE, BAD_INPUTS, DUPLICATE, SUCCESS, UNKNOWN };

    private final Queue<Block>              blockChain          = new ConcurrentLinkedQueue<Block>();
    private final Queue<Transaction>        transactions        = new ConcurrentLinkedQueue<Transaction>();
    private final Queue<Transaction>        unused              = new ConcurrentLinkedQueue<Transaction>();

    private static final byte[]             INITIAL_HASH        = new byte[0];

    private static final Transaction        GENESIS_TRANS;
    private static final Block              GENESIS_BLOCK;

    static {
        final Transaction[] empty = new Transaction[0];
        final Transaction[] output = new Transaction[1];
        output[0] = new Transaction(NO_ONE, GENESIS_NAME, "Genesis gets 50 coins.", 50, empty, empty);

        GENESIS_TRANS = new Transaction(NO_ONE, GENESIS_NAME, "Genesis transfer.", 0, empty, output);

        final ByteBuffer buffer = ByteBuffer.allocate(GENESIS_TRANS.getBufferLength());
        GENESIS_TRANS.toBuffer(buffer);
        buffer.flip();

        final byte[] bytes = buffer.array();
        final byte[] nextHash = BlockChain.getNextHash(BlockChain.INITIAL_HASH, bytes);

        GENESIS_BLOCK = new Block(NO_ONE, BlockChain.INITIAL_HASH, nextHash, GENESIS_TRANS);
    }

    private byte[]                          latestHash          = INITIAL_HASH;

    public BlockChain() {
        // transfer initial coins into genesis account
        this.addBlock(GENESIS_BLOCK);
    }

    public synchronized Queue<Transaction> getUnused() {
        return unused;
    }

    // synchronized to protected hash from changing while processing
    public synchronized Block getNextBlock(String from, Transaction transaction) {
        final ByteBuffer buffer = ByteBuffer.allocate(transaction.getBufferLength());
        transaction.toBuffer(buffer);
        buffer.flip();

        final byte[] bytes = buffer.array();

        final byte[] nextHash = getNextHash(latestHash, bytes);
        return (new Block(from, latestHash, nextHash, transaction));
    }

    // synchronized to protected hash from changing while processing
    public synchronized BlockChainStatus checkBlock(Block block) {
        final Transaction transaction = block.transaction;
        final ByteBuffer buffer = ByteBuffer.allocate(transaction.getBufferLength());
        transaction.toBuffer(buffer);
        buffer.flip();

        final byte[] bytes = buffer.array();
        final byte[] nextHash = getNextHash(latestHash, bytes);

        final byte[] incomingHash = block.hash;
        if (!(Arrays.equals(incomingHash, nextHash))) {
            System.err.println("Invalid hash on transaction.");
            return BlockChainStatus.BAD_SIGNATURE;
        }

        return BlockChainStatus.SUCCESS;
    }

    // synchronized to protected hash/transactions from changing while processing
    public synchronized BlockChainStatus addBlock(Block block) {
        // Already processed this block? Happens if a miner is slow and isn't first to send the block
        if (blockChain.contains(block))
            return BlockChainStatus.DUPLICATE;

        // Check to see if the block's hash is correct
        final BlockChainStatus status = checkBlock(block);
        if (status != BlockChainStatus.SUCCESS)
            return status;

        final Transaction transaction = block.transaction;
        final ByteBuffer buffer = ByteBuffer.allocate(transaction.getBufferLength());
        transaction.toBuffer(buffer);
        buffer.flip();

        final byte[] bytes = buffer.array();
        final byte[] nextHash = getNextHash(latestHash, bytes);

        // Remove the input transactions exist in the unused pool
        for (Transaction t : transaction.inputs) {
            boolean exists = unused.remove(t);
            if (exists == false) {
                System.err.println("Bad inputs in block. block={\n"+block.toString()+"\n}");
                return BlockChainStatus.BAD_INPUTS;
            }
        }

        // Add output to unused transactions
        for (Transaction t : transaction.outputs)
            unused.add(t);

        // Update the hash and add the new transaction to the list
        latestHash = nextHash;
        transactions.add(transaction);
        blockChain.add(block);
        return BlockChainStatus.SUCCESS;
    }

    // synchronized to protected transactions from changing while processing
    public synchronized long getBalance(String name) {
        long result = 0;
        for (Transaction t : transactions) {
            // Remove the inputs
            for (Transaction c : t.inputs) {
                if (name.equals(c.to))
                    result -= c.value;
            }
            // Add the outputs
            for (Transaction c : t.outputs) {
                if (name.equals(c.to))
                    result += c.value;
            }
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
        final BlockChain b = (BlockChain) o;
        for (Transaction c : transactions) {
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
    public synchronized String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("hash=[").append(BlockChain.bytesToHex(latestHash)).append("]\n");
        builder.append("inputs={").append("\n");
        for (Transaction c : transactions) {
            for (Transaction i : c.inputs)
                builder.append('\t').append(i.value).append(" from '").append(i.from).append("' to '").append(i.to).append("'\n");
            builder.append("}\n");
        }
        builder.append("outputs={").append("\n");
        for (Transaction c : transactions) {
            for (Transaction i : c.outputs)
                builder.append('\t').append(i.value).append(" from '").append(i.from).append("' to '").append(i.to).append("'\n");
            builder.append("}\n");
        }
        return builder.toString();
    }
}
