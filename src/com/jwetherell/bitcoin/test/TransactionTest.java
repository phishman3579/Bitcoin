package com.jwetherell.bitcoin.test;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.data_model.Coin;
import com.jwetherell.bitcoin.data_model.Transaction;

public class TransactionTest {

    @Test
    public void testSerialization() {
        String f = "me";
        String t = "you";
        String m = "Here is a coin for you!";
        int v = 1;

        Coin c1 = new Coin(f, t, m,v);
        byte[] prev = "I am a hash!".getBytes();
        byte[] hash = "I am also a hash!".getBytes();
        Transaction trans1 = new Transaction(f,prev,hash,c1);
        ByteBuffer b = ByteBuffer.allocate(trans1.getBufferLength());
        trans1.toBuffer(b);

        Transaction trans2 = new Transaction();
        trans2.fromBuffer(b);

        Assert.assertTrue(trans1.equals(trans2));
    }
}
