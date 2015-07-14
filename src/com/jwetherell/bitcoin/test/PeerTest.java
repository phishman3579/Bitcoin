package com.jwetherell.bitcoin.test;

import java.util.Queue;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.Listener;
import com.jwetherell.bitcoin.Receiver;
import com.jwetherell.bitcoin.data_model.Coin;
import com.jwetherell.bitcoin.data_model.Data;
import com.jwetherell.bitcoin.data_model.Peer;
import com.jwetherell.bitcoin.data_model.Wallet;
import com.jwetherell.bitcoin.networking.UDP;

public class PeerTest {

    // Simply exchange one coin from one peer to another using UDP
    @Test
    public void coinExchange() throws InterruptedException {

        // Serialize the coin into bytes
        final Coin c1 = new Coin("I give you 1 coin", 1);

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
                    c2.fromBytes(data);

                    Assert.assertTrue(c1.data.equals(c2.data));
                    Assert.assertTrue(c1.value == c2.value);
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
        Data data = new Data(recv.getHost(), recv.getPort(), c1.toBytes());
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
    @Test
    public void coin2Exchange() throws InterruptedException {

        // Coins
        final Coin c1 = new Coin("I give you 1 coin", 1);
        final Coin c2 = new Coin("I give you 2 coins", 2);

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
                    c3.fromBytes(data);

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
        Coin t1 = w1.removeCoin(2);
        Data data1 = new Data(recv.getHost(), recv.getPort(), t1.toBytes());
        Queue<Data> q = send.getQueue();
        q.add(data1);

        // Send 1
        Coin t2 = w1.removeCoin(1);
        Data data2 = new Data(recv.getHost(), recv.getPort(), t2.toBytes());
        q.add(data2);

        // Start the sender
        Thread s = new Thread(send);
        s.start();

        // Wait for threads to finish exchanging coins
        r.join();
        s.join();

        Assert.assertTrue(toRecv == w2.getBalance());
    }

    @Test
    public void testPeers() throws InterruptedException {
        String n1 = "n1";
        Peer p1 = new Peer(n1);
        p1.getWallet().addCoin(new Coin("Coinage.",10));

        Thread.yield();

        String n2 = "n2";
        Peer p2 = new Peer(n2);
        p2.getWallet().addCoin(new Coin("Coinage.",20));

        Thread.yield();

        String n3 = "n3";
        Peer p3 = new Peer(n3);
        p3.getWallet().addCoin(new Coin("Coinage.",15));

        Thread.sleep(250);

        boolean sent = p1.sendCoin(n2, 3);
        while (!sent) {
            // Wait and try again
            Thread.sleep(250);
            Thread.yield();
            sent = p1.sendCoin(n2, 3);
        }

        sent = p3.sendCoin(n2, 7);
        while (!sent) {
            // Wait and try again
            Thread.sleep(250);
            Thread.yield();
            sent = p3.sendCoin(n2, 7);
        }

        while (p2.getWallet().getBalance()!=30) {
            Thread.sleep(1000);
        }

        p1.shutdown();
        p2.shutdown();
        p3.shutdown();
    }
}
