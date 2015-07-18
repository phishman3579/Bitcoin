package com.jwetherell.bitcoin.test;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.data_model.Data;

public class DataTest {

    @Test
    public void testSerialization() {
        final String from = "me";
        final String sHost = "127.0.0.1";
        final int sPort = 1024;
        final String to = "you";
        final String dHost = "localhost";
        final int dPort = 1025;
        final byte[] sig = "sig".getBytes();
        final byte[] msg = "This is a message".getBytes();

        final Data d1 = new Data(from,sHost,sPort,to,dHost,dPort,sig,msg);
        final ByteBuffer buffer = ByteBuffer.allocate(d1.getBufferLength());
        d1.toBuffer(buffer);
        buffer.flip();

        final Data d2 = new Data();
        d2.fromBuffer(buffer);

        Assert.assertTrue(d1.equals(d2));
    }
}
