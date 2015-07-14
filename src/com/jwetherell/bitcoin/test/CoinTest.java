package com.jwetherell.bitcoin.test;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.data_model.Coin;

public class CoinTest {

    @Test
    public void testSerialization() {
        String f = "me";
        String t = "you";
        String m = "Here is a coin for you!";
        int v = 1;

        Coin c1 = new Coin(f, t, m,v);
        ByteBuffer b = ByteBuffer.allocate(c1.getBufferLength());
        c1.toBuffer(b);

        Coin c2 = new Coin();
        c2.fromBuffer(b);

        Assert.assertTrue(c1.equals(c2));
    }
}
