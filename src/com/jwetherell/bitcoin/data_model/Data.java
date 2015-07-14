package com.jwetherell.bitcoin.data_model;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class Data {

    private static final int    lengthLength    = 4;

    private final ByteBuffer    lengthBytes     = ByteBuffer.allocate(lengthLength);

    public InetAddress          addr;
    public int                  port;
    public ByteBuffer           data;

    public Data() { }

    public Data(String host, int port, byte[] bytes) {
        try {
            this.addr = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        this.port = port;
        this.data = ByteBuffer.allocate(bytes.length);
        this.data.put(bytes);
    }

    public void toBuffer(ByteBuffer b) {
        final byte[] buffer = b.array();

        final byte[] hBytes = addr.getHostAddress().getBytes();
        final int hLength = hBytes.length;
        lengthBytes.clear();
        lengthBytes.putInt(hLength);
        int pos = 0;
        System.arraycopy(lengthBytes.array(), 0, buffer, pos, lengthLength);
        pos += lengthLength;
        System.arraycopy(hBytes, 0, buffer, pos, hLength);
        pos += hLength;

        final String sPort = String.valueOf(port);
        final byte[] pBytes = sPort.getBytes();
        final int pLength = pBytes.length;
        lengthBytes.clear();
        lengthBytes.putInt(pLength);
        System.arraycopy(lengthBytes.array(), 0, buffer, pos, lengthLength);
        pos += lengthLength;
        System.arraycopy(pBytes, 0, buffer, pos, pLength);
        pos += pLength;

        data.flip();
        data.get(buffer, pos, data.limit());
    }

    public void fromBuffer(ByteBuffer b) {
        final int hLength = b.getInt();
        final byte[] hostBytes = new byte[hLength];
        b.get(hostBytes);
        String sHost = new String(hostBytes);
        try {
            this.addr = InetAddress.getByName(sHost);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        final int pLength = b.getInt();
        final byte[] portBytes = new byte[pLength];
        b.get(portBytes);
        String sPort = new String(portBytes);
        this.port = Integer.parseInt(sPort);

        int bLength = b.remaining();
        byte[] bytes = new byte[bLength];
        b.get(bytes, 0, bLength);
        this.data = ByteBuffer.allocate(bytes.length);
        this.data.put(bytes);
        this.data.flip();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("addr=").append(addr.getHostAddress()).append(":").append(port).append("\n");
        builder.append("data=").append(new String(data.asCharBuffer().array()));
        return builder.toString();
    }
}
