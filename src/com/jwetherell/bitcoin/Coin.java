package com.jwetherell.bitcoin;

import java.nio.ByteBuffer;

public class Coin {

    private static final int VALUE_LENGTH = 4;

    private final ByteBuffer intBytes = ByteBuffer.allocate(VALUE_LENGTH);

    public String data;
    public int value;

    public Coin() { }

    public Coin(String data, int value) {
        this.data = data;
        this.value = value;
    }

    public byte[] toBytes() {
        intBytes.putInt(value);
        byte[] iBytes = intBytes.array();
        byte[] mBytes = data.getBytes();
        byte[] bytes = new byte[iBytes.length + mBytes.length];
        System.arraycopy(iBytes, 0, bytes, 0, iBytes.length);
        System.arraycopy(mBytes, 0, bytes, VALUE_LENGTH, mBytes.length);
        return bytes;
    }

    public void fromBytes(byte[] bytes) {
        ByteBuffer wrapped = ByteBuffer.wrap(bytes);
        value = wrapped.getInt();
        byte[] mBytes = new byte[bytes.length - VALUE_LENGTH];
        wrapped.get(mBytes);
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
        builder.append("data=[").append(data).append("]\n");
        builder.append("value=").append(value);
        return builder.toString();
    }
}
