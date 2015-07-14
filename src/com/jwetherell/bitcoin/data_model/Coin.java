package com.jwetherell.bitcoin.data_model;

import java.nio.ByteBuffer;

public class Coin {

    private static final int    VALUE_LENGTH    = 4;
    private static final int    MSG_LENGTH      = 4;
    private static final int    FROM_LENGTH     = 4;
    private static final int    TO_LENGTH       = 4;

    public String               from;
    public String               to;
    public String               msg;
    public int                  value;

    public Coin() { }

    public Coin(Coin c) {
        this.from = c.from;
        this.to = c.to;
        this.msg = c.msg;
        this.value = c.value;
    }

    public Coin(String from, String to, String msg, int value) {
        if (value<=0)
            throw new RuntimeException("Cannot have a zero or negative value coin");
        this.from = from;
        this.to = to;
        this.msg = msg;
        this.value = value;
    }

    public int getBufferLength() {
        return  VALUE_LENGTH + 
                MSG_LENGTH + msg.getBytes().length + 
                FROM_LENGTH + from.getBytes().length + 
                TO_LENGTH + to.getBytes().length;
    }

    public void toBuffer(ByteBuffer buffer) {
        buffer.putInt(value);

        final int mLength = msg.length();
        buffer.putInt(mLength);
        final byte[] mBytes = msg.getBytes();
        buffer.put(mBytes);

        final byte[] fBytes = from.getBytes();
        buffer.putInt(fBytes.length);
        buffer.put(fBytes);

        final byte[] oBytes = to.getBytes();
        buffer.putInt(oBytes.length);
        buffer.put(oBytes);

        buffer.flip();
    }

    public void fromBuffer(ByteBuffer buffer) {
        value = buffer.getInt();

        final int mLength = buffer.getInt();
        final byte[] mBytes = new byte[mLength];
        buffer.get(mBytes, 0, mLength);
        msg = new String(mBytes);

        final int fLength = buffer.getInt();
        final byte[] fBytes = new byte[fLength];
        buffer.get(fBytes, 0, fLength);
        from = new String(fBytes);

        final int oLength = buffer.getInt();
        final byte[] oBytes = new byte[oLength];
        buffer.get(oBytes, 0, oLength);
        to = new String(oBytes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Coin))
            return false;
        Coin c = (Coin) o;
        if (!(c.from.equals(this.from)))
            return false;
        if (!(c.to.equals(this.to)))
            return false;
        if (!(c.msg.equals(this.msg)))
            return false;
        if (c.value != this.value)
            return false;
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("from='").append(from).append("'\n");
        builder.append("to='").append(to).append("'\n");
        builder.append("msg=[").append(msg).append("]\n");
        builder.append("value=").append(value);
        return builder.toString();
    }
}
