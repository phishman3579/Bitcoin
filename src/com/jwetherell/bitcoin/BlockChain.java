package com.jwetherell.bitcoin;

import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.jwetherell.bitcoin.common.Constants;
import com.jwetherell.bitcoin.common.HashUtils;
import com.jwetherell.bitcoin.data_model.Block;
import com.jwetherell.bitcoin.data_model.Transaction;

public class BlockChain {

    public static final String              NO_ONE              = "no one";
    public static final Signature           NO_ONE_SIGNATURE;
    public static final byte[]              NO_ONE_PUB_KEY;
    static {
        try {
            final KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA", "SUN");
            final SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
            gen.initialize(512, random);

            final KeyPair pair = gen.generateKeyPair();
            final PrivateKey privateKey = pair.getPrivate();
            NO_ONE_SIGNATURE = Signature.getInstance("SHA1withDSA", "SUN");
            NO_ONE_SIGNATURE.initSign(privateKey);

            final PublicKey publicKey = pair.getPublic();
            NO_ONE_PUB_KEY = publicKey.getEncoded();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public static final String              GENESIS_NAME        = "genesis";

    protected static final boolean          DEBUG               = Boolean.getBoolean("debug");

    private final List<Block>               blockChain          = new CopyOnWriteArrayList<Block>();
    private final List<Transaction>         transactions        = new CopyOnWriteArrayList<Transaction>();
    private final List<Transaction>         unused              = new CopyOnWriteArrayList<Transaction>();

    private static final byte[]             INITIAL_HASH        = new byte[0];

    private static final Transaction        GENESIS_TRANS;
    private static final Block              GENESIS_BLOCK;
    static {
        // To start the block chain, we have an initial transaction which has no inputs and one output which
        // is given to the genesis entity. This is the only transaction which has no inputs.
        final Transaction[] empty = new Transaction[0];
        final Transaction[] output = new Transaction[1];
        final String outputMsg = "Genesis gets 50 coins.";
        output[0] = Transaction.newSignedTransaction(NO_ONE_SIGNATURE, NO_ONE, GENESIS_NAME, outputMsg, 50, empty, empty);

        final String msg = "Genesis transfer.";
        GENESIS_TRANS = Transaction.newSignedTransaction(NO_ONE_SIGNATURE, NO_ONE, GENESIS_NAME, msg, 0, empty, output);

        final ByteBuffer buffer = ByteBuffer.allocate(GENESIS_TRANS.getBufferLength());
        GENESIS_TRANS.toBuffer(buffer);
        buffer.flip();

        final byte[] bytes = buffer.array();
        final byte[] nextHash = BlockChain.getNextHash(BlockChain.INITIAL_HASH, bytes);

        GENESIS_BLOCK = new Block(NO_ONE, BlockChain.INITIAL_HASH, nextHash, GENESIS_TRANS, 0);
        GENESIS_BLOCK.confirmed = true;
    }

    private final String                    owner;

    private volatile byte[]                 latestHash          = INITIAL_HASH;

    public BlockChain(String owner) {
        this.owner = owner;
        // transfer initial coins to genesis entity
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

    public Constants.Status checkHash(Block block) {
        if (block.blockLength > this.blockChain.size()) {
            // This block is in the future
            if (DEBUG)
                System.out.println(owner+" found a future block. lengths="+this.blockChain.size()+"\n"+"block={\n"+block.toString()+"\n}");
            return Constants.Status.FUTURE_BLOCK;
        }

        final Transaction transaction = block.transaction;
        final ByteBuffer buffer = ByteBuffer.allocate(transaction.getBufferLength());
        transaction.toBuffer(buffer);
        buffer.flip();

        // Calculate what I think the next has should be
        final byte[] bytes = buffer.array();
        final byte[] nextHash = getNextHash(latestHash, bytes);

        // Store the previous and next hash from the block
        final byte[] incomingPrev = block.prev;
        final byte[] incomingHash = block.hash;

        if (!(Arrays.equals(incomingHash, nextHash))) {
            // This block has a different 'next' hash then I expect, someone is out of sync
            if (DEBUG) {
                StringBuilder builder = new StringBuilder();
                builder.append(owner).append(" Invalid hash on transaction from '").append(block.transaction.from).append("'\n");
                builder.append("confirmed="+block.confirmed).append("\n");
                builder.append("length=").append(this.blockChain.size()).append("\n");
                builder.append("latest=["+HashUtils.bytesToHex(latestHash)+"]\n");
                builder.append("next=["+HashUtils.bytesToHex(nextHash)+"]\n");
                builder.append("incomingLength=").append(block.blockLength).append("\n");
                builder.append("incomingPrev=["+HashUtils.bytesToHex(incomingPrev)+"]\n");
                builder.append("incomingNext=["+HashUtils.bytesToHex(incomingHash)+"]\n");
                System.err.println(builder.toString());
            }
            return Constants.Status.BAD_HASH;
        }

        return Constants.Status.SUCCESS;
    }

    public Constants.Status addBlock(Block block) {
        // Already processed this block? Happens if a miner is slow and isn't first to confirm the block
        if (blockChain.contains(block))
            return Constants.Status.DUPLICATE;

        // Check to see if the block's hash is what I expect
        final Constants.Status status = checkHash(block);
        if (status != Constants.Status.SUCCESS)
            return status;

        // Get the aggregate transaction for processing
        final Transaction transaction = block.transaction;

        // Remove the inputs from the unused pool
        for (Transaction t : transaction.inputs) {
            boolean exists = unused.remove(t);
            if (exists == false) {
                if (DEBUG)
                    System.err.println(owner+" Bad inputs in block. block={\n"+block.toString()+"\n}");
                return Constants.Status.BAD_INPUTS;
            }
        }

        // Add outputs to unused pool
        for (Transaction t : transaction.outputs)
            unused.add(t);

        // Update the hash and add the new transaction to the list
        final byte[] nextHash = block.hash;
        blockChain.add(block);
        transactions.add(transaction);
        latestHash = nextHash;

        if (DEBUG) {
            final String prev = HashUtils.bytesToHex(latestHash);
            final String next = HashUtils.bytesToHex(nextHash);
            final StringBuilder builder = new StringBuilder();
            builder.append(owner).append(" updated hash from '").append(block.transaction.from).append("'\n");
            builder.append("block chain length=").append(this.blockChain.size()).append("\n");
            builder.append("prev=[").append(prev).append("]\n");
            builder.append("next=[").append(next).append("]\n");
            System.err.println(builder.toString());
        }

        return Constants.Status.SUCCESS;
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
        final StringBuilder builder = new StringBuilder();
        builder.append(HashUtils.bytesToHex(hash)).append(HashUtils.bytesToHex(bytes));
        final String string = builder.toString();
        final byte[] output = HashUtils.calculateSha256(string);
        return output;
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
        builder.append("hash=[").append(HashUtils.bytesToHex(latestHash)).append("]\n");
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
