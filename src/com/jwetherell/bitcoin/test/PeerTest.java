package com.jwetherell.bitcoin.test;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.Peer;
import com.jwetherell.bitcoin.data_model.Transaction;

public class PeerTest {

    @Test
    public void testHello() {
        final byte[] key = "key".getBytes();
        final byte[] p1 = Peer.getIamMsg(key);
        final byte[] k = Peer.parseIamMsg(p1);

        Assert.assertTrue(Arrays.equals(key, k));
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
        final Transaction c1 = new Transaction("me","you","I give you 1 block", 1);
        byte[] b = Peer.getTransactionMsg(c1);
        final Transaction c2 = Peer.parseTransactionMsg(b);

        Assert.assertTrue(c1.equals(c2));
    }

    @Test
    public void testCoinAck() {
        final Transaction c1 = new Transaction("me","you","I give you 1 block", 1);
        byte[] b = Peer.getTransactionAckMsg(c1);
        final Transaction c2 = Peer.parseTransactionAckMsg(b);

        Assert.assertTrue(c1.equals(c2));
    }
}
