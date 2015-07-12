package com.jwetherell.bitcoin.test;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.Coin;
import com.jwetherell.bitcoin.test.ClientTest.RunnableRecv;
import com.jwetherell.bitcoin.test.ClientTest.RunnableSend;

public class LogicTest {

    // Simply exchange one coin from one peer to another
    @Test
    public void coinExchange() throws InterruptedException {
        String d = "Hello world";
        int v = 1;

        // Serialize the coin into bytes
        Coin c1 = new Coin(d,v);
        byte[] toSend = c1.toBytes();

        // Start both the sender and receiver
        byte[] toRecv = new byte[toSend.length];
        Thread r = new Thread(new RunnableRecv(toRecv));
        r.start();     
        Thread s = new Thread(new RunnableSend(toSend));
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
