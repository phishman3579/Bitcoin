package com.jwetherell.bitcoin.data_model;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Data {

    private static final int    LENGTH_LENGTH    = 4;

    public InetAddress          sourceAddr;
    public int                  sourcePort;
    public InetAddress          destAddr;
    public int                  destPort;
    public ByteBuffer           data;

    public Data() { }

    public Data(String sourceAddr, int sourcePort, String destAddr, int destPort, byte[] bytes) {
        try {
            this.sourceAddr = InetAddress.getByName(sourceAddr);
            this.destAddr = InetAddress.getByName(destAddr);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        this.sourcePort = sourcePort;
        this.destPort = destPort;
        this.data = ByteBuffer.allocate(bytes.length);
        this.data.put(bytes);
        data.flip();
    }

    public int getBufferLength() {
        return  LENGTH_LENGTH + sourceAddr.getHostAddress().getBytes().length + 
                LENGTH_LENGTH + String.valueOf(sourcePort).getBytes().length + 
                LENGTH_LENGTH + destAddr.getHostAddress().getBytes().length + 
                LENGTH_LENGTH + String.valueOf(destPort).getBytes().length + 
                data.limit();
    }

    public void toBuffer(ByteBuffer buffer) {
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

        buffer.put(data);

        buffer.flip();
    }

    public void fromBuffer(ByteBuffer buffer) {
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

        final int bLength = buffer.remaining();
        final byte[] bytes = new byte[bLength];
        buffer.get(bytes, 0, bLength);
        this.data = ByteBuffer.allocate(bytes.length);
        this.data.put(bytes);
        this.data.flip();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Data))
            return false;
        Data d = (Data) o;
        if (!(sourceAddr.equals(d.sourceAddr)))
            return false;
        if (sourcePort != d.sourcePort)
            return false;
        if (!(destAddr.equals(d.destAddr)))
            return false;
        if (destPort != d.destPort)
            return false;
        if (!(Arrays.equals(this.data.array(), d.data.array())))
            return false;
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("source=").append(sourceAddr.getHostAddress()).append(":").append(sourcePort).append("\n");
        builder.append("destination=").append(destAddr.getHostAddress()).append(":").append(destPort).append("\n");
        builder.append("data=").append(new String(data.asCharBuffer().array()));
        return builder.toString();
    }
}
