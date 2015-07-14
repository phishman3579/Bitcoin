package com.jwetherell.bitcoin.test;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.data_model.Data;

public class DataTest {

    @Test
    public void testSerialization() {
        final String sHost = "127.0.0.1";
        final int sPort = 1024;
        final String dHost = "localhost";
        final int dPort = 1025;
        final byte[] msg = "This is a message".getBytes();

        final Data d1 = new Data(sHost,sPort,dHost,dPort,msg);
        ByteBuffer b = ByteBuffer.allocate(d1.getBufferLength());
        d1.toBuffer(b);

        final Data d2 = new Data();
        d2.fromBuffer(b);

        Assert.assertTrue(d1.equals(d2));
    }
}
