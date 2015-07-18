package com.jwetherell.bitcoin.test;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.data_model.Transaction;

public class BlockTest {

    @Test
    public void testSerialization() {
        String f = "me";
        String t = "you";
        String m = "Here is a block for you!";
        int v = 1;

        Transaction c1 = new Transaction(f, t, m,v);
        ByteBuffer b = ByteBuffer.allocate(c1.getBufferLength());
        c1.toBuffer(b);

        Transaction c2 = new Transaction();
        c2.fromBuffer(b);

        Assert.assertTrue(c1.equals(c2));
    }
}
