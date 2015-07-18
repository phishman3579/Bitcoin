package com.jwetherell.bitcoin.data_model;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class Transaction {

    private static final int    BOOLEAN_LENGTH          = 2;
    private static final int    NUM_OF_ZEROS_LENGTH     = 4;
    private static final int    NONCE_LENGTH            = 8;
    private static final int    LENGTH_LENGTH           = 4;

    public boolean              isValid         = false;
    public int                  numberOfZeros;
    public long                 nonce;
    public Block                block;
    public byte[]               prev;
    public byte[]               hash;

    public Transaction() {
        block = new Block();
        prev = new byte[]{};
        hash = new byte[]{};
    }

    public Transaction(String from, byte[] prevHash, byte[] hash, Block block) {
        this.prev = prevHash;
        this.hash = hash;
        this.block = block;
    }

    public int getBufferLength() {
        return  BOOLEAN_LENGTH + 
                NUM_OF_ZEROS_LENGTH +
                NONCE_LENGTH +
                LENGTH_LENGTH + prev.length + 
                LENGTH_LENGTH + hash.length + 
                block.getBufferLength();
    }

    public void toBuffer(ByteBuffer buffer) {
        buffer.putChar(getBoolean(isValid));
        buffer.putInt(numberOfZeros);
        buffer.putLong(nonce);

        buffer.putInt(prev.length);
        buffer.put(prev);

        buffer.putInt(hash.length);
        buffer.put(hash);

        block.toBuffer(buffer);
    }

    public void fromBuffer(ByteBuffer buffer) {
        isValid = parseBoolean(buffer.getChar());
        numberOfZeros = buffer.getInt();
        nonce = buffer.getLong();

        {
            final int length = buffer.getInt();
            prev = new byte[length];
            buffer.get(prev);
        }

        {
            final int length = buffer.getInt();
            hash = new byte[length];
            buffer.get(hash);
        }

        block.fromBuffer(buffer);
    }

    private static final char getBoolean(boolean bool) {
        return (bool?'T':'F');
    }

    private static final boolean parseBoolean(char bool) {
        return (bool=='T'?true:false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Transaction))
            return false;
        Transaction c = (Transaction) o;
        if (isValid != c.isValid)
            return false;
        if (nonce != c.nonce)
            return false;
        if (numberOfZeros != c.numberOfZeros)
            return false;
        if (!(c.block.equals(this.block)))
            return false;
        if (!(Arrays.equals(c.prev, prev)))
            return false;
        if (!(Arrays.equals(c.hash, hash)))
            return false;
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("isValid=").append(isValid).append("\n");
        builder.append("numberOfZerosToCompute=").append(numberOfZeros).append("\n");
        builder.append("nonce=").append(nonce).append("\n");
        builder.append("prev=[").append(BlockChain.bytesToHex(prev)).append("]\n");
        builder.append("hash=[").append(BlockChain.bytesToHex(hash)).append("]\n");
        builder.append("block={").append("\n");
        builder.append(block.toString()).append("\n");
        builder.append("}");
        return builder.toString();
    }
}
