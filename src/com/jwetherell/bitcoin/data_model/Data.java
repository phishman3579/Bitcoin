package com.jwetherell.bitcoin.data_model;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Data {

    private static final int    LENGTH_LENGTH   = 4;
    private static final int    FROM_LENGTH     = 4;
    private static final int    TO_LENGTH       = 4;

    public String               from;
    public String               to;
    public InetAddress          sourceAddr;
    public int                  sourcePort;
    public InetAddress          destAddr;
    public int                  destPort;
    public ByteBuffer           signature;
    public ByteBuffer           message;

    public Data() { }

    public Data(String from, String sourceAddr, int sourcePort, 
                String to, String destAddr, int destPort, 
                byte[] signature, byte[] bytes) {
        this.from = from;
        this.to = to;
        try {
            this.sourceAddr = InetAddress.getByName(sourceAddr);
            this.destAddr = InetAddress.getByName(destAddr);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        this.sourcePort = sourcePort;
        this.destPort = destPort;

        this.signature = ByteBuffer.allocate(signature.length);
        this.signature.put(signature);
        this.signature.flip();

        this.message = ByteBuffer.allocate(bytes.length);
        this.message.put(bytes);
        this.message.flip();
    }

    public int getBufferLength() {
        return  FROM_LENGTH + from.getBytes().length +
                TO_LENGTH + to.getBytes().length +
                LENGTH_LENGTH + sourceAddr.getHostAddress().getBytes().length + 
                LENGTH_LENGTH + String.valueOf(sourcePort).getBytes().length + 
                LENGTH_LENGTH + destAddr.getHostAddress().getBytes().length + 
                LENGTH_LENGTH + String.valueOf(destPort).getBytes().length + 
                LENGTH_LENGTH + signature.limit() +
                LENGTH_LENGTH + message.limit();
    }

    public void toBuffer(ByteBuffer buffer) {
        final byte[] fBytes = from.getBytes();
        buffer.putInt(fBytes.length);
        buffer.put(fBytes);

        final byte[] oBytes = to.getBytes();
        buffer.putInt(oBytes.length);
        buffer.put(oBytes);

        { // Source
            final byte[] hBytes = sourceAddr.getHostAddress().getBytes();
            final int hLength = hBytes.length;
            buffer.putInt(hLength);
            buffer.put(hBytes);
    
            final byte[] pBytes = String.valueOf(sourcePort).getBytes();
            final int pLength = pBytes.length;
            buffer.putInt(pLength);
            buffer.put(pBytes);
        }

        { // Destination
            final byte[] hBytes = destAddr.getHostAddress().getBytes();
            final int hLength = hBytes.length;
            buffer.putInt(hLength);
            buffer.put(hBytes);
    
            final byte[] pBytes = String.valueOf(destPort).getBytes();
            final int pLength = pBytes.length;
            buffer.putInt(pLength);
            buffer.put(pBytes);
        }

        // Sig
        buffer.putInt(signature.limit());
        buffer.put(signature);

        // Data
        buffer.putInt(message.limit());
        buffer.put(message);

        buffer.flip();
    }

    public void fromBuffer(ByteBuffer buffer) {
        final int fLength = buffer.getInt();
        final byte[] fBytes = new byte[fLength];
        buffer.get(fBytes, 0, fLength);
        from = new String(fBytes);

        final int oLength = buffer.getInt();
        final byte[] oBytes = new byte[oLength];
        buffer.get(oBytes, 0, oLength);
        to = new String(oBytes);

        { // Source
            final int hLength = buffer.getInt();
            final byte[] hostBytes = new byte[hLength];
            buffer.get(hostBytes);
            final String sHost = new String(hostBytes);
            try {
                this.sourceAddr = InetAddress.getByName(sHost);
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
    
            final int pLength = buffer.getInt();
            final byte[] portBytes = new byte[pLength];
            buffer.get(portBytes);
            final String sPort = new String(portBytes);
            this.sourcePort = Integer.parseInt(sPort);
        }

        { // Destination
            final int hLength = buffer.getInt();
            final byte[] hostBytes = new byte[hLength];
            buffer.get(hostBytes);
            final String sHost = new String(hostBytes);
            try {
                this.destAddr = InetAddress.getByName(sHost);
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
    
            final int pLength = buffer.getInt();
            final byte[] portBytes = new byte[pLength];
            buffer.get(portBytes);
            final String sPort = new String(portBytes);
            this.destPort = Integer.parseInt(sPort);
        }

        // Sig
        final int sLength = buffer.getInt();
        final byte[] sBytes = new byte[sLength];
        buffer.get(sBytes, 0, sLength);
        this.signature = ByteBuffer.allocate(sBytes.length);
        this.signature.put(sBytes);

        // Data
        final int dLength = buffer.getInt();
        final byte[] dBytes = new byte[dLength];
        buffer.get(dBytes, 0, dLength);
        this.message = ByteBuffer.allocate(dBytes.length);
        this.message.put(dBytes);

        this.message.flip();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Data))
            return false;
        Data d = (Data) o;
        if (!(d.from.equals(this.from)))
            return false;
        if (!(d.to.equals(this.to)))
            return false;
        if (!(sourceAddr.equals(d.sourceAddr)))
            return false;
        if (sourcePort != d.sourcePort)
            return false;
        if (!(destAddr.equals(d.destAddr)))
            return false;
        if (destPort != d.destPort)
            return false;
        if (!(Arrays.equals(this.signature.array(), d.signature.array())))
            return false;
        if (!(Arrays.equals(this.message.array(), d.message.array())))
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
        builder.append("source=").append(sourceAddr.getHostAddress()).append(":").append(sourcePort).append("\n");
        builder.append("to='").append(to).append("'\n");
        builder.append("destination=").append(destAddr.getHostAddress()).append(":").append(destPort).append("\n");
        builder.append("data=").append(new String(message.array()));
        return builder.toString();
    }
}
