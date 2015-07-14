package com.jwetherell.bitcoin.data_model;

import java.nio.ByteBuffer;

public class Coin {

    private static final int    VALUE_LENGTH    = 4;
    private static final int    DATA_LENGTH     = 4;

    private final ByteBuffer    valueBytes      = ByteBuffer.allocate(VALUE_LENGTH);
    private final ByteBuffer    dateBytes       = ByteBuffer.allocate(DATA_LENGTH);

    public String               data;
    public int                  value;

    public Coin() { }

    public Coin(String data, int value) {
        if (value<=0)
            throw new RuntimeException("Cannot have a zero or negative value coin");
        this.data = data;
        this.value = value;
    }

    public byte[] toBytes() {
        valueBytes.putInt(value);
        final byte[] iBytes = valueBytes.array();

        final int length = data.length();
        dateBytes.putInt(length);
        final byte[] lBytes = dateBytes.array();
        final byte[] mBytes = data.getBytes();

        final byte[] bytes = new byte[iBytes.length + lBytes.length + length];
        System.arraycopy(iBytes, 0, bytes, 0, iBytes.length);
        System.arraycopy(lBytes, 0, bytes, VALUE_LENGTH, lBytes.length);
        System.arraycopy(mBytes, 0, bytes, VALUE_LENGTH+DATA_LENGTH, mBytes.length);

        return bytes;
    }

    public void fromBytes(byte[] bytes) {
        final ByteBuffer wrapped = ByteBuffer.wrap(bytes);
        value = wrapped.getInt();

        final int length = wrapped.getInt();
        final byte[] mBytes = new byte[length];
        wrapped.get(mBytes, 0, length);
        data = new String(mBytes);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Coin))
            return false;
        Coin c = (Coin) o;
        if (!(c.data.equals(this.data)))
            return false;
        if (c.value != this.value)
            return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("data=[").append(data).append("] ");
        builder.append("value=").append(value);
        return builder.toString();
    }
}
