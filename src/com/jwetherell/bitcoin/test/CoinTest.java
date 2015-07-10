package com.jwetherell.bitcoin.test;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.Coin;

public class CoinTest {

    @Test
    public void testPositiveSerialization() {
        String d = "Here is a coin for you!";
        int v = 1;

        Coin c1 = new Coin(d,v);
        byte[] b = c1.toBytes();

        Coin c2 = new Coin();
        c2.fromBytes(b);

        Assert.assertTrue(c1.data.equals(c2.data));
        Assert.assertTrue(c1.value == c2.value);
        Assert.assertTrue(c1.equals(c2));
    }

    @Test
    public void testNegativeSerialization() {
        String d = "Here is a coin for you!";
        int v = -1;

        Coin c1 = new Coin(d,v);
        byte[] b = c1.toBytes();

        Coin c2 = new Coin();
        c2.fromBytes(b);

        Assert.assertTrue(c1.data.equals(c2.data));
        Assert.assertTrue(c1.value == c2.value);
        Assert.assertTrue(c1.equals(c2));
    }
}
