package com.jwetherell.bitcoin.test;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.data_model.Transaction;
import com.jwetherell.bitcoin.data_model.Block;

public class TransactionTest {

    @Test
    public void testSerialization() {
        String f = "me";
        String t = "you";
        String m = "Here is a coin for you!";
        int v = 1;

        Transaction c1 = new Transaction(f, t, m,v);
        byte[] prev = "I am a hash!".getBytes();
        byte[] hash = "I am also a hash!".getBytes();
        Block trans1 = new Block(f,prev,hash,c1);
        ByteBuffer b = ByteBuffer.allocate(trans1.getBufferLength());
        trans1.toBuffer(b);

        Block trans2 = new Block();
        trans2.fromBuffer(b);

        Assert.assertTrue(trans1.equals(trans2));
    }
}
