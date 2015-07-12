package com.jwetherell.bitcoin.test;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.Coin;
import com.jwetherell.bitcoin.Listener;
import com.jwetherell.bitcoin.Multicast;
import com.jwetherell.bitcoin.UDP;

public class PeerTest {

    // Simply exchange one coin from one peer to another using multicast
    @Test
    public void multicastCoinExchange() throws InterruptedException {
        Listener listener = new Listener() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void onMessage(byte[] data) {
                System.out.println("Listener received '"+new String(data)+"'");
                Multicast.Peer.RunnableRecv.run = false;
            }
        };

        String d = "Hello world";
        int v = 1;

        // Serialize the coin into bytes
        Coin c1 = new Coin(d,v);
        byte[] toSend = c1.toBytes();

        // Start both the sender and receiver
        byte[] toRecv = new byte[toSend.length];
        Thread r = new Thread(new Multicast.Peer.RunnableRecv(listener,toRecv));
        r.start();     
        Thread s = new Thread(new Multicast.Peer.RunnableSend(toSend));
        s.start();

        // Wait for threads to finish exchanging coins
        r.join();
        s.join();

        Coin c2 = new Coin();
        c2.fromBytes(toRecv);

        Assert.assertTrue(c1.data.equals(c2.data));
        Assert.assertTrue(c1.value == c2.value);
        Assert.assertTrue(c1.equals(c2));
    }

    // Simply exchange one coin from one peer to another using UDP
    @Test
    public void udpCoinExchange() throws InterruptedException {
        Listener   listener    = new Listener() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void onMessage(byte[] data) {
                System.out.println("Listener received '"+new String(data)+"'");
                UDP.Peer.RunnableRecv.run = false;
            }
        };

        String d = "Hello world";
        int v = 1;

        // Serialize the coin into bytes
        Coin c1 = new Coin(d,v);
        byte[] toSend = c1.toBytes();

        // Start both the sender and receiver
        byte[] toRecv = new byte[toSend.length];
        Thread r = new Thread(new UDP.Peer.RunnableRecv(listener,toRecv));
        r.start();     
        Thread s = new Thread(new UDP.Peer.RunnableSend(toSend));
        s.start();

        // Wait for threads to finish exchanging coins
        r.join();
        s.join();

        Coin c2 = new Coin();
        c2.fromBytes(toRecv);

        Assert.assertTrue(c1.data.equals(c2.data));
        Assert.assertTrue(c1.value == c2.value);
        Assert.assertTrue(c1.equals(c2));
    }
}
