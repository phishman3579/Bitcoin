package com.jwetherell.bitcoin.data_model;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class Transaction {

    private static final int    BOOLEAN_LENGTH = 2;
    private static final int    LENGTH_LENGTH   = 4;

    private boolean             isValid         = false;
    private Coin                coin;
    private byte[]              hash;

    public Transaction() {
        coin = new Coin();
        hash = new byte[]{};
    }

    public Transaction(byte[] prevHash, Coin coin) {
        this.hash = prevHash;
        this.coin = coin;
    }

    public boolean getIsValid() {
        return isValid;
    }

    public void setIsValid(boolean value) {
        this.isValid = value;
    }

    public Coin getCoin() {
        return coin;
    }

    public byte[] getHash() {
        return hash;
    }

    public int getBufferLength() {
        return  BOOLEAN_LENGTH + 
                LENGTH_LENGTH + hash.length + 
                coin.getBufferLength();
    }

    public void toBuffer(ByteBuffer buffer) {
        buffer.putChar(getBoolean(isValid));
        buffer.putInt(hash.length);
        buffer.put(hash);
        coin.toBuffer(buffer);
    }

    public void fromBuffer(ByteBuffer buffer) {
        isValid = parseBoolean(buffer.getChar());
        final int length = buffer.getInt();
        hash = new byte[length];
        buffer.get(hash);
        coin.fromBuffer(buffer);
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
        if (!(c.coin.equals(this.coin)))
            return false;
        if (!(Arrays.equals(hash, c.hash)))
            return false;
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("hash=[").append(new String(hash)).append("]\n");
        builder.append(coin.toString());
        return builder.toString();
    }
}
