package com.jwetherell.bitcoin.test;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.Peer;
import com.jwetherell.bitcoin.data_model.Coin;

public class PeerTest {

    @Test
    public void testHello() {
        final String hello = "hello";
        final byte[] p1 = Peer.getIamMsg(hello);
        final String result = Peer.parseIamMsg(p1);

        Assert.assertTrue(hello.equals(result));
    }

    @Test
    public void testWhois() {
        final String hello = "hello";
        final byte[] p1 = Peer.getWhoisMsg(hello);
        final String result = Peer.parseWhoisMsg(p1);

        Assert.assertTrue(hello.equals(result));
    }

    @Test
    public void testCoin() {
        final Coin c1 = new Coin("me","you","I give you 1 coin", 1);
        byte[] b = Peer.getCoinMsg(c1);
        final Coin c2 = Peer.parseCoinMsg(b);

        Assert.assertTrue(c1.equals(c2));
    }

    @Test
    public void testCoinAck() {
        final Coin c1 = new Coin("me","you","I give you 1 coin", 1);
        byte[] b = Peer.getCoinAck(c1);
        final Coin c2 = Peer.parseCoinAck(b);

        Assert.assertTrue(c1.equals(c2));
    }
}
