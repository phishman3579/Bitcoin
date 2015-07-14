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

    @Test(timeout=1000)
    public void testPeers() throws InterruptedException {
        String n1 = "n1";
        Peer p1 = new Peer(n1);
        p1.getWallet().addCoin(new Coin("me","you","Coinage.",10));

        Thread.yield();

        String n2 = "n2";
        Peer p2 = new Peer(n2);
        p2.getWallet().addCoin(new Coin("me","you","Coinage.",20));

        Thread.yield();

        String n3 = "n3";
        Peer p3 = new Peer(n3);
        p3.getWallet().addCoin(new Coin("me","you","Coinage.",15));

        Thread.yield();

        p1.sendCoin(n2, 3);
        // p1=7, p2=23, p3=15
        p2.sendCoin(n3, 7);
        // p1=7, p2=16, p3=22
        p3.sendCoin(n1, 11);
        // p1=18, p2=16, p3=11

        while (p1.getWallet().getBalance()!=18 || p2.getWallet().getBalance()!=16 || p3.getWallet().getBalance()!=11) {
            Thread.yield();
        }

        p1.shutdown();
        p2.shutdown();
        p3.shutdown();

        Assert.assertTrue(p1.getWallet().getPending()==0);
        Assert.assertTrue(p2.getWallet().getPending()==0);
        Assert.assertTrue(p3.getWallet().getPending()==0);

        Assert.assertTrue(p1.getWallet().getBalance()==18);
        Assert.assertTrue(p2.getWallet().getBalance()==16);
        Assert.assertTrue(p3.getWallet().getBalance()==11);
    }

    @Test(timeout=1000)
    public void testDupCoin() throws InterruptedException {
        String n1 = "n1";
        String n2 = "n2";
        Coin c1 = new Coin(n1,n2,"Coinage.",10);
        Peer p1 = new Peer(n1);
        Peer p2 = new Peer(n2);

        // Send coin
        Thread.yield();
        p1.sendCoin(n2,c1);
        Thread.yield();
        while (p2.getWallet().getBalance()!=10) {
            Thread.yield();
        }
        Assert.assertTrue(p2.getWallet().getBalance()==10);

        // Send dup coin and a new coin
        Thread.yield();
        // This is a dup and should be dropped
        p1.sendCoin(n2,c1); 
        Thread.yield();
        // This should be accepted
        p1.sendCoin(n2,20);
        Thread.yield();
        while (p2.getWallet().getBalance()!=30) {
            Thread.yield();
        }
        Assert.assertTrue(p2.getWallet().getBalance()==30);

        p1.shutdown();
        p2.shutdown();
    }
}
