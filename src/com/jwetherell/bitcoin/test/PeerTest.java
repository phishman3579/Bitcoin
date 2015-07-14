package com.jwetherell.bitcoin.test;

import java.nio.ByteBuffer;
import java.util.Queue;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.Peer;
import com.jwetherell.bitcoin.data_model.Coin;
import com.jwetherell.bitcoin.data_model.Data;
import com.jwetherell.bitcoin.data_model.Wallet;
import com.jwetherell.bitcoin.interfaces.Listener;
import com.jwetherell.bitcoin.interfaces.Receiver;
import com.jwetherell.bitcoin.networking.UDP;

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

    // Simply exchange one coin from one peer to another using UDP
    @Test(timeout=10)
    public void coinExchange() throws InterruptedException {

        // Serialize the coin into bytes
        final Coin c1 = new Coin("me","you","I give you 1 coin", 1);

        final Listener listener = new Listener() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void onMessage(Receiver recv) {
                Data d = recv.getQueue().poll();
                while (d != null) {
                    final byte[] data = d.data.array();
                    System.out.println("Listener received '"+new String(data)+"'");

                    final Coin c2 = new Coin();
                    c2.fromBuffer(d.data);

                    Assert.assertTrue(c1.equals(c2));

                    d = recv.getQueue().poll();
                }
                Thread.yield();

                UDP.Peer.RunnableSend.run = false;
                UDP.Peer.RunnableRecv.run = false;
            }
        };

        // Start the receiver
        UDP.Peer.RunnableRecv recv = new UDP.Peer.RunnableRecv(listener);
        Thread r = new Thread(recv);
        r.start();

        // Send the coins
        UDP.Peer.RunnableSend send = new UDP.Peer.RunnableSend();
        // Send
        ByteBuffer b = ByteBuffer.allocate(c1.getBufferLength());
        c1.toBuffer(b);
        Data data = new Data(recv.getHost(), recv.getPort(), recv.getHost(), recv.getPort(), b.array());
        Queue<Data> q = send.getQueue();
        q.add(data);

        // Start the sender
        Thread s = new Thread(send);
        s.start();

        // Wait for threads to finish exchanging coins
        r.join();
        s.join();
    }

    // Exchange two coins from one peer to another using UDP and test wallet
    @Test(timeout=10)
    public void coin2Exchange() throws InterruptedException {

        // Coins
        final Coin c1 = new Coin("me","you","I give you 1 coin", 1);
        final Coin c2 = new Coin("me","you","I give you 2 coins", 2);

        // Add coins to waller
        final Wallet w1 = new Wallet("w1");
        w1.addCoin(c1);
        w1.addCoin(c2);
        final long toRecv = w1.getBalance();

        final Wallet w2 = new Wallet("w2");

        final Listener listener = new Listener() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void onMessage(Receiver recv) {
                Data d = recv.getQueue().poll();
                while (d != null) {
                    final byte[] data = d.data.array();
                    System.out.println("Listener received '"+new String(data)+"'");

                    final Coin c3 = new Coin();
                    c3.fromBuffer(d.data);

                    // Put a coin
                    w2.addCoin(c3);

                    d = recv.getQueue().poll();
                }
                Thread.yield();

                if (toRecv == w2.getBalance()) {
                    UDP.Peer.RunnableSend.run = false;
                    UDP.Peer.RunnableRecv.run = false;
                }
            }
        };

        // Start the receiver
        UDP.Peer.RunnableRecv recv = new UDP.Peer.RunnableRecv(listener);
        Thread r = new Thread(recv);
        r.start();

        // Send the coins
        UDP.Peer.RunnableSend send = new UDP.Peer.RunnableSend();

        // Send 2
        Coin t1 = w1.removeCoin("none",2);
        ByteBuffer b1 = ByteBuffer.allocate(t1.getBufferLength());
        t1.toBuffer(b1);
        Data data1 = new Data(recv.getHost(), recv.getPort(), recv.getHost(), recv.getPort(), b1.array());
        Queue<Data> q = send.getQueue();
        q.add(data1);

        // Send 1
        Coin t2 = w1.removeCoin("none",1);
        ByteBuffer b2 = ByteBuffer.allocate(t2.getBufferLength());
        t2.toBuffer(b2);
        Data data2 = new Data(recv.getHost(), recv.getPort(), recv.getHost(), recv.getPort(), b2.array());
        q.add(data2);

        // Start the sender
        Thread s = new Thread(send);
        s.start();

        // Wait for threads to finish exchanging coins
        r.join();
        s.join();

        Assert.assertTrue(toRecv == w2.getBalance());
    }

    @Test//(timeout=1000)
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
}
