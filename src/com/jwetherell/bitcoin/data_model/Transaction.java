package com.jwetherell.bitcoin.data_model;

import java.nio.ByteBuffer;
import java.security.Signature;
import java.util.Arrays;

import com.jwetherell.bitcoin.common.HashUtils;
import com.jwetherell.bitcoin.common.KeyUtils;

public class Transaction {

    private static final int    FROM_LENGTH         = 4;
    private static final int    TO_LENGTH           = 4;
    private static final int    TIMESTAMP_LENGTH    = 8;
    private static final int    HEADER_LENGTH       = 4;
    private static final int    VALUE_LENGTH        = 4;
    private static final int    LENGTH_LENGTH       = 4;

    public String               from;
    public String               to;
    public long                 timestamp;
    public String               header;
    public int                  value;
    public ByteBuffer           signature;

    public Transaction[]        inputs;
    public Transaction[]        outputs;

    public Transaction() { }

    public Transaction(String from, String to, String header, int value, byte[] signature, Transaction[] inputs, Transaction[] outputs) {
        this.from = from;
        this.to = to;
        this.timestamp = 0;
        this.header = header;
        this.value = value;
        final byte[] sig = new byte[signature.length];
        System.arraycopy(signature, 0, sig, 0, signature.length);
        this.signature = ByteBuffer.wrap(sig);

        this.inputs = new Transaction[inputs.length];
        for (int i=0; i<inputs.length; i++)
            this.inputs[i] = inputs[i];

        this.outputs = new Transaction[outputs.length];
        for (int i=0; i<outputs.length; i++)
            this.outputs[i] = outputs[i];
    }

    /** Create the aggregate Transaction and sign it **/
    public static final Transaction newSignedTransaction(Signature signature, 
                                                         String from, String to, 
                                                         String header, int value, 
                                                         Transaction[] inputs, Transaction[] outputs)
    {
        final byte[] sig = KeyUtils.signMsg(signature, header.getBytes());
        Transaction transaction = new Transaction(from, to, header, value, sig, inputs, outputs);
        return transaction;
    }

    public void updateTimestamp() {
        this.timestamp = System.currentTimeMillis();
    }

    public int getBufferLength() {
        int iLength = 0;
        for (Transaction t : inputs)
            iLength += LENGTH_LENGTH + t.getBufferLength();

        int oLength = 0;
        for (Transaction t : outputs)
            oLength += LENGTH_LENGTH + t.getBufferLength();

        int length =    LENGTH_LENGTH + signature.limit() +
                        LENGTH_LENGTH + iLength +
                        LENGTH_LENGTH + oLength +
                        TIMESTAMP_LENGTH +
                        VALUE_LENGTH +
                        HEADER_LENGTH + header.getBytes().length + 
                        FROM_LENGTH + from.getBytes().length + 
                        TO_LENGTH + to.getBytes().length;
        return length;
    }

    public void toBuffer(ByteBuffer buffer) {
        { // signature
            buffer.putInt(signature.limit());
            buffer.put(signature);
            signature.flip();
        }

        { // inputs
            buffer.putInt(inputs.length);
            for (Transaction t : inputs) {
                buffer.putInt(t.getBufferLength());
                t.toBuffer(buffer);
                // do not flip buffer here
            }
        }

        { // outputs
            buffer.putInt(outputs.length);
            for (Transaction t : outputs) {
                buffer.putInt(t.getBufferLength());
                t.toBuffer(buffer);
                // do not flip buffer here
            }
        }

        buffer.putLong(timestamp);
        buffer.putInt(value);

        final int mLength = header.length();
        buffer.putInt(mLength);
        final byte[] mBytes = header.getBytes();
        buffer.put(mBytes);

        final byte[] fBytes = from.getBytes();
        buffer.putInt(fBytes.length);
        buffer.put(fBytes);

        final byte[] oBytes = to.getBytes();
        buffer.putInt(oBytes.length);
        buffer.put(oBytes);
    }

    public void fromBuffer(ByteBuffer buffer) {
        { // signature
            int sLength = buffer.getInt();
            byte[] bSignature = new byte[sLength];
            buffer.get(bSignature);
            this.signature = ByteBuffer.wrap(bSignature);
        }

        { // inputs
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

        { // ouputs
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

        timestamp = buffer.getLong();
        value = buffer.getInt();

        final int mLength = buffer.getInt();
        final byte[] mBytes = new byte[mLength];
        buffer.get(mBytes, 0, mLength);
        header = new String(mBytes);

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
    public int hashCode() {
        int hashCode = 0;
        hashCode += from.length();
        hashCode += to.length();
        hashCode += timestamp;
        hashCode += header.length();
        hashCode += value;
        for (byte b :signature.array())
            hashCode += b;
        for (Transaction t : inputs)
            hashCode += t.hashCode();
        for (Transaction t : outputs)
            hashCode += t.hashCode();
        return 31 * hashCode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Transaction))
            return false;
        Transaction c = (Transaction) o;
        { // signature
            if (c.signature.limit() != this.signature.limit())
                return false;
            if (!(Arrays.equals(c.signature.array(), this.signature.array())))
                return false;
        }
        { // inputs
            if (c.inputs.length != this.inputs.length)
                return false;
            for (int i=0; i<c.inputs.length; i++)
                if (!(c.inputs[i].equals(inputs[i])))
                    return false;
        }
        { // ouputs
            if (c.outputs.length != this.outputs.length)
                return false;
            for (int i=0; i<c.outputs.length; i++)
                if (!(c.outputs[i].equals(outputs[i])))
                    return false;
        }
        if (c.timestamp != this.timestamp)
            return false;
        if (c.value != this.value)
            return false;
        if (!(c.from.equals(this.from)))
            return false;
        if (!(c.to.equals(this.to)))
            return false;
        if (!(c.header.equals(this.header)))
            return false;
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("signature=[").append(HashUtils.bytesToHex(this.signature.array())).append("]\n");
        builder.append("inputs=").append(inputs.length).append("\n");
        builder.append("outputs=").append(outputs.length).append("\n");
        builder.append("time='").append(timestamp).append("'\n");
        builder.append("value='").append(value).append("'\n");
        builder.append("from='").append(from).append("'\n");
        builder.append("to='").append(to).append("'\n");
        builder.append("header=[").append(header).append("]");
        return builder.toString();
    }
}
