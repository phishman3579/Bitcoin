package com.jwetherell.bitcoin.test;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.data_model.Transaction;

public class TransactionTest {

    @Test
    public void testSerialization() {
        final String f = "me";
        final String t = "you";
        final String m = "Here is a block for you!";
        final int v = 1;

        final Transaction trans = new Transaction(f, t, m,v);
        final ByteBuffer b = ByteBuffer.allocate(trans.getBufferLength());
        trans.toBuffer(b);

        final Transaction trans2 = new Transaction();
        trans2.fromBuffer(b);

        Assert.assertTrue(trans.equals(trans2));
    }
}
