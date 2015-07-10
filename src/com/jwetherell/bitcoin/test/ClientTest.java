package com.jwetherell.bitcoin.test;

import java.io.IOException;
import java.net.MulticastSocket;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.Peer;

public class ClientTest {

    // TTL for send
    private static final int ttl = 10;

    @Test
    public void testClient() throws InterruptedException {
        byte[] toSend = "Hello world.".getBytes();
        byte[] toRecv = new byte[toSend.length];

        // Start both the sender and receiver
        Thread r = new Thread(new RunnableRecv(toRecv));
        r.start();     
        Thread s = new Thread(new RunnableSend(toSend));
        s.start();

        // Wait for threads to finish
        r.join();
        s.join();

        Assert.assertTrue(Arrays.equals(toSend, toRecv));
    }

    static final class RunnableRecv implements Runnable {

        private final byte[] toRecv;

        public RunnableRecv(byte[] toRecv) {
            this.toRecv = toRecv;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            MulticastSocket r = null;
            try {
                r = Peer.createReceiver();
                Peer.recvData(r,toRecv);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    Peer.destoryReceiver(r);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    static final class RunnableSend implements Runnable {

        private final byte[] toSend;

        public RunnableSend(byte[] toSend) {
            this.toSend = toSend;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            MulticastSocket s = null;
            try {
                s = Peer.createSender();
                Peer.sendData(s,ttl,toSend);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    Peer.destroySender(s);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    public static void main(String[] args) throws Exception {
        byte[] toRecv = new byte[1024];
        byte[] toSend = "Hello world.".getBytes();
        if (args.length==1) {
            String toStart = args[0];
            if (toStart.equals(Peer.RECEIVER)) {
                Thread r = new Thread(new RunnableRecv(toRecv));
                r.start(); 
            } else if (toStart.equals(Peer.SENDER)) {
                Thread s = new Thread(new RunnableSend(toSend));
                s.start();
            } else {
                System.err.println("Unhandled. arg="+toStart);
            }
        } else {
            System.err.println("Unhandled number of arguments. args="+args.length);
        }
    }

}
