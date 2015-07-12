package com.jwetherell.bitcoin.test;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.Listener;
import com.jwetherell.bitcoin.Multicast;

public class MulticastTest {

    public static final String      SENDER      = "S";
    public static final String      RECEIVER    = "R";

    private static final Listener   listener    = new Listener() {
        /**
         * {@inheritDoc}
         */
        @Override
        public void onMessage(byte[] data) {
            System.out.println("Listener received '"+new String(data)+"'");
            Multicast.Peer.RunnableRecv.run = false;
        }
    };

    @Test
    public void test() throws InterruptedException {
        byte[] toSend = "Hello world.".getBytes();
        byte[] toRecv = new byte[toSend.length];

        // Start both the sender and receiver
        Thread r = new Thread(new Multicast.Peer.RunnableRecv(listener,toRecv));
        r.start();     
        Thread s = new Thread(new Multicast.Peer.RunnableSend(toSend));
        s.start();

        // Wait for threads to finish
        r.join();
        s.join();

        Assert.assertTrue(Arrays.equals(toSend, toRecv));
    }

    public static void main(String[] args) throws Exception {
        byte[] toRecv = new byte[1024];
        byte[] toSend = "Hello world.".getBytes();
        if (args.length==1) {
            String toStart = args[0];
            if (toStart.equals(RECEIVER)) {
                Thread r = new Thread(new Multicast.Peer.RunnableRecv(listener,toRecv));
                r.start(); 
            } else if (toStart.equals(SENDER)) {
                Thread s = new Thread(new Multicast.Peer.RunnableSend(toSend));
                s.start();
            } else {
                System.err.println("Unhandled. arg="+toStart);
            }
        } else {
            System.err.println("Unhandled number of arguments. args="+args.length);
        }
    }

}
