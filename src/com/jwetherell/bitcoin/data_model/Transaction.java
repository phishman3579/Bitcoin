package com.jwetherell.bitcoin.data_model;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class Transaction {

    private static final int    BOOLEAN_LENGTH  = 2;
    private static final int    LENGTH_LENGTH   = 4;
    private static final int    FROM_LENGTH     = 4;

    private boolean             isValid         = false;
    public String               from;
    private Coin                coin;
    private byte[]              hash;

    public Transaction() {
        from = "";
        coin = new Coin();
        hash = new byte[]{};
    }

    public Transaction(String from, byte[] prevHash, Coin coin) {
        this.from = from;
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
                FROM_LENGTH + from.getBytes().length + 
                LENGTH_LENGTH + hash.length + 
                coin.getBufferLength();
    }

    public void toBuffer(ByteBuffer buffer) {
        buffer.putChar(getBoolean(isValid));

        final byte[] fBytes = from.getBytes();
        buffer.putInt(fBytes.length);
        buffer.put(fBytes);

        buffer.putInt(hash.length);
        buffer.put(hash);

        coin.toBuffer(buffer);
    }

    public void fromBuffer(ByteBuffer buffer) {
        isValid = parseBoolean(buffer.getChar());

        final int fLength = buffer.getInt();
        final byte[] fBytes = new byte[fLength];
        buffer.get(fBytes, 0, fLength);
        from = new String(fBytes);

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
        if (isValid != c.isValid)
            return false;
        if (!(from.equals(this.from)))
            return false;
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
        builder.append("isValid=").append(isValid).append("\n");
        builder.append("from=").append(from).append("\n");
        builder.append("coin={").append("\n");
        builder.append(coin.toString()).append("\n");
        builder.append("}\n");
        return builder.toString();
    }
}
