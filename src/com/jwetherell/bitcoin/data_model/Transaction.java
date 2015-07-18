package com.jwetherell.bitcoin.data_model;

import java.nio.ByteBuffer;

public class Transaction {

    private static final int    FROM_LENGTH     = 4;
    private static final int    TO_LENGTH       = 4;
    private static final int    MSG_LENGTH      = 4;
    private static final int    VALUE_LENGTH    = 4;
    private static final int    LENGTH_LENGTH   = 4;

    public String               from;
    public String               to;
    public String               msg;
    public int                  value;

    public Transaction[]        inputs;
    public Transaction[]        outputs;

    public Transaction() { }

    public Transaction(String from, String to, String msg, int value, Transaction[] inputs, Transaction[] outputs) {
        this.from = from;
        this.to = to;
        this.msg = msg;
        this.value = value;
        this.inputs = new Transaction[inputs.length];
        for (int i=0; i<inputs.length; i++)
            this.inputs[i] = inputs[i];
        this.outputs = new Transaction[outputs.length];
        for (int i=0; i<outputs.length; i++)
            this.outputs[i] = outputs[i];
    }

    public int getBufferLength() {
        int iLength = 0;
        for (Transaction t : inputs)
            iLength += LENGTH_LENGTH + t.getBufferLength();
        int oLength = 0;
        for (Transaction t : outputs)
            oLength += LENGTH_LENGTH + t.getBufferLength();
        int length =    LENGTH_LENGTH + iLength +
                        LENGTH_LENGTH + oLength +
                        VALUE_LENGTH +
                        MSG_LENGTH + msg.getBytes().length + 
                        FROM_LENGTH + from.getBytes().length + 
                        TO_LENGTH + to.getBytes().length;
        return length;
    }

    public void toBuffer(ByteBuffer buffer) {
        {
            buffer.putInt(inputs.length);
            for (Transaction t : inputs) {
                buffer.putInt(t.getBufferLength());
                t.toBuffer(buffer);
                // do not flip buffer here
            }
        }

        {
            buffer.putInt(outputs.length);
            for (Transaction t : outputs) {
                buffer.putInt(t.getBufferLength());
                t.toBuffer(buffer);
                // do not flip buffer here
            }
        }

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
    }

    public void fromBuffer(ByteBuffer buffer) {
        {
            int iLength = buffer.getInt();
            this.inputs = new Transaction[iLength];
            for (int i=0; i<iLength; i++) {
                int tLength = buffer.getInt();
                Transaction t = new Transaction();
                final byte[] bytes = new byte[tLength];
                buffer.get(bytes);
                ByteBuffer bb = ByteBuffer.wrap(bytes);
                t.fromBuffer(bb);
                this.inputs[i] = t;
            }
        }

        {
            int oLength = buffer.getInt();
            this.outputs = new Transaction[oLength];
            for (int i=0; i<oLength; i++) {
                int tLength = buffer.getInt();
                Transaction t = new Transaction();
                final byte[] bytes = new byte[tLength];
                buffer.get(bytes);
                ByteBuffer bb = ByteBuffer.wrap(bytes);
                t.fromBuffer(bb);
                this.outputs[i] = t;
            }
        }

        value = buffer.getInt();

        final int mLength = buffer.getInt();
        final byte[] mBytes = new byte[mLength];
        buffer.get(mBytes, 0, mLength);
        msg = new String(mBytes);

        final int fLength = buffer.getInt();
        final byte[] fBytes = new byte[fLength];
        buffer.get(fBytes, 0, fLength);
        from = new String(fBytes);

        final int tLength = buffer.getInt();
        final byte[] tBytes = new byte[tLength];
        buffer.get(tBytes, 0, tLength);
        to = new String(tBytes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Transaction))
            return false;
        Transaction c = (Transaction) o;
        {
            if (c.inputs.length != this.inputs.length)
                return false;
            for (int i=0; i<c.inputs.length; i++)
                if (!(c.inputs[i].equals(inputs[i])))
                    return false;
        }
        {
            if (c.outputs.length != this.outputs.length)
                return false;
            for (int i=0; i<c.outputs.length; i++)
                if (!(c.outputs[i].equals(outputs[i])))
                    return false;
        }
        if (c.value != this.value)
            return false;
        if (!(c.from.equals(this.from)))
            return false;
        if (!(c.to.equals(this.to)))
            return false;
        if (!(c.msg.equals(this.msg)))
            return false;
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("inputs=").append(inputs.length).append("\n");
        builder.append("outputs=").append(outputs.length).append("\n");
        builder.append("value='").append(value).append("'\n");
        builder.append("from='").append(from).append("'\n");
        builder.append("to='").append(to).append("'\n");
        builder.append("msg=[").append(msg).append("]\n");
        return builder.toString();
    }
}
