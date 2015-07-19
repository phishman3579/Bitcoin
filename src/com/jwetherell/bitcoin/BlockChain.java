package com.jwetherell.bitcoin;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.jwetherell.bitcoin.data_model.Block;
import com.jwetherell.bitcoin.data_model.Transaction;

public class BlockChain {

    public static final String              NO_ONE              = "no one";
    public static final String              GENESIS_NAME        = "genesis";

    public static enum                      BlockChainStatus    { NO_PUBLIC_KEY, FUTURE_BLOCK, BAD_HASH, BAD_SIGNATURE, BAD_INPUTS, DUPLICATE, SUCCESS, UNKNOWN };

    protected static final boolean          DEBUG               = Boolean.getBoolean("debug");

    private final List<Block>               blockChain          = new CopyOnWriteArrayList<Block>();
    private final List<Transaction>         transactions        = new CopyOnWriteArrayList<Transaction>();
    private final List<Transaction>         unused              = new CopyOnWriteArrayList<Transaction>();

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

        GENESIS_BLOCK = new Block(NO_ONE, BlockChain.INITIAL_HASH, nextHash, GENESIS_TRANS, 0);
    }

    private final String                    owner;

    private volatile byte[]                 latestHash          = INITIAL_HASH;

    public BlockChain(String owner) {
        this.owner = owner;
        // transfer initial coins into genesis account
        this.addBlock(GENESIS_BLOCK);
    }

    public int getLength() {
        return blockChain.size();
    }

    public Block getBlock(int blockNumber) {
        if (blockNumber>=blockChain.size())
            return null;
        return blockChain.get(blockNumber);
    }

    public List<Transaction> getUnused() {
        return unused;
    }

    public Block getNextBlock(String from, Transaction transaction) {
        final ByteBuffer buffer = ByteBuffer.allocate(transaction.getBufferLength());
        transaction.toBuffer(buffer);
        buffer.flip();

        final byte[] bytes = buffer.array();
        final byte[] nextHash = getNextHash(latestHash, bytes);
        return (new Block(from, latestHash, nextHash, transaction, this.blockChain.size()));
    }

    public BlockChainStatus checkHash(Block block) {
        if (block.blockLength > this.blockChain.size()) {
            // This block is in the future, wait for the block confirmation
            if (DEBUG)
                System.err.println(owner+" found a future block. lengths="+this.blockChain.size()+"\n"+"block={\n"+block.toString()+"\n}");
            return BlockChainStatus.FUTURE_BLOCK;
        }

        final Transaction transaction = block.transaction;
        final ByteBuffer buffer = ByteBuffer.allocate(transaction.getBufferLength());
        transaction.toBuffer(buffer);
        buffer.flip();

        final byte[] bytes = buffer.array();
        final byte[] nextHash = getNextHash(latestHash, bytes);

        final byte[] incomingPrev = block.prev;
        final byte[] incomingHash = block.hash;

        if (!(Arrays.equals(incomingHash, nextHash))) {
            if (DEBUG) {
                StringBuilder builder = new StringBuilder();
                builder.append(owner).append(" Invalid hash on transaction from '").append(block.transaction.from).append("'\n");
                builder.append("confirmed="+block.confirmed).append("\n");
                builder.append("length=").append(this.blockChain.size()).append("\n");
                builder.append("latest=["+bytesToHex(latestHash)+"]\n");
                builder.append("next=["+bytesToHex(nextHash)+"]\n");
                builder.append("incomingLength=").append(block.blockLength).append("\n");
                builder.append("incomingPrev=["+bytesToHex(incomingPrev)+"]\n");
                builder.append("incomingNext=["+bytesToHex(incomingHash)+"]\n");
                System.err.println(builder.toString());
            }
            return BlockChainStatus.BAD_HASH;
        }

        return BlockChainStatus.SUCCESS;
    }

    // synchronized to protected transactions/blockChain/unused from changing while processing
    public synchronized BlockChainStatus addBlock(Block block) {
        // Already processed this block? Happens if a miner is slow and isn't first to send the block
        if (blockChain.contains(block))
            return BlockChainStatus.DUPLICATE;

        // Check to see if the block's hash is correct
        final BlockChainStatus status = checkHash(block);
        if (status != BlockChainStatus.SUCCESS)
            return status;

        final Transaction transaction = block.transaction;
        final ByteBuffer buffer = ByteBuffer.allocate(transaction.getBufferLength());
        transaction.toBuffer(buffer);
        buffer.flip();

        final byte[] bytes = buffer.array();
        final byte[] nextHash = getNextHash(latestHash, bytes);

        // Remove the inputs from the unused pool
        for (Transaction t : transaction.inputs) {
            boolean exists = unused.remove(t);
            if (exists == false) {
                if (DEBUG)
                    System.err.println(owner+" Bad inputs in block. block={\n"+block.toString()+"\n}");
                return BlockChainStatus.BAD_INPUTS;
            }
        }

        // Add outputs to unused transactions
        for (Transaction t : transaction.outputs)
            unused.add(t);

        // Update the hash and add the new transaction to the list
        final String prev = bytesToHex(latestHash);
        final String next = bytesToHex(nextHash);

        latestHash = nextHash;
        blockChain.add(block);
        transactions.add(transaction);

        if (DEBUG) {
            final StringBuilder builder = new StringBuilder();
            builder.append(owner).append(" updated hash from '").append(block.transaction.from).append("'\n");
            builder.append("length=").append(this.blockChain.size()).append("\n");
            builder.append("prev=[").append(prev).append("]\n");
            builder.append("next=[").append(next).append("]\n");
            System.err.println(builder.toString());
        }

        return BlockChainStatus.SUCCESS;
    }

    public long getBalance(String name) {
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
        final StringBuilder result = new StringBuilder();
        for (byte byt : bytes) 
            result.append(Integer.toString((byt & 0xff) + 0x100, 16).substring(1));
        return result.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BlockChain))
            return false;
        final BlockChain b = (BlockChain) o;
        for (Transaction t : transactions) {
            if (!(b.transactions.contains(t)))
                return false;
        }
        return true;
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
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
