package com.jwetherell.bitcoin.test;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.data_model.Transaction;

public class TransactionTest {

    private static final Transaction[] EMPTY = new Transaction[0];

    @Test
    public void testSerialization() {
        final String f = "me";
        final String t = "you";

        final Transaction[] inputs = new Transaction[2];
        inputs[0] = new Transaction(f, t, "Here is a coin for you!", 1, EMPTY, EMPTY);
        inputs[1] = new Transaction(f, t, "Here is 2 coins for you!", 2, EMPTY, EMPTY);

        final Transaction[] outputs = new Transaction[1];
        outputs[0] = new Transaction(t, f, "Here is three coins for you!", 3, EMPTY, EMPTY);

        final Transaction trans = new Transaction(f, t, "Here is a transaction", 0, inputs, outputs);
        final ByteBuffer buffer = ByteBuffer.allocate(trans.getBufferLength());
        trans.toBuffer(buffer);
        buffer.flip();

        final Transaction trans2 = new Transaction();
        trans2.fromBuffer(buffer);

        Assert.assertTrue(trans.equals(trans2));
    }
}
