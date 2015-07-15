package com.jwetherell.bitcoin.test;

import java.util.Queue;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.data_model.Data;
import com.jwetherell.bitcoin.interfaces.Listener;
import com.jwetherell.bitcoin.interfaces.Receiver;
import com.jwetherell.bitcoin.networking.TCP;

public class TCPTest {

    public static final String      SENDER      = "S";
    public static final String      RECEIVER    = "R";

    @Test
    public void test() throws InterruptedException {
        final byte[] key = "key".getBytes();
        final byte[] sig = "sig".getBytes();
        final byte[] toSend = "Hello world.".getBytes();
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
                    Assert.assertTrue(isEquals(toSend,data,toSend.length));
                    d = recv.getQueue().poll();
                }
                TCP.Peer.RunnableRecv.run = false;
                TCP.Peer.RunnableSend.run = false;
            }
        };

        // Start both the sender and receiver
        TCP.Peer.RunnableRecv recv = new TCP.Peer.RunnableRecv(listener);
        final Thread r = new Thread(recv);
        r.start();     

        final TCP.Peer.RunnableSend send = new TCP.Peer.RunnableSend();
        final Thread s = new Thread(send);
        s.start();

        // Wait for everyone to initialize
        Thread.sleep(250);

        final Data data = new Data(recv.getHost(), recv.getPort(), recv.getHost(), recv.getPort(), sig, key, toSend);
        send.getQueue().add(data);

        // Wait for threads to finish
        r.join();
        s.join();
    }

    public static void main(String[] args) throws Exception {
        final byte[] key = "key".getBytes();
        final byte[] sig = "sig".getBytes();
        final byte[] toSend = "Hello world.".getBytes();
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
                    Assert.assertTrue(isEquals(toSend,data,toSend.length));
                    d = recv.getQueue().poll();
                }
                TCP.Peer.RunnableRecv.run = false;
            }
        };

        if (args.length==1) {
            String toStart = args[0];
            if (toStart.equals(RECEIVER)) {
                final Thread r = new Thread(new TCP.Peer.RunnableRecv(listener));
                r.start(); 
            } else if (toStart.equals(SENDER)) {
                final TCP.Peer.RunnableSend send = new TCP.Peer.RunnableSend();
                Queue<Data> q = send.getQueue();
                final Data data = new Data(TCP.LOCAL, TCP.port, TCP.LOCAL, TCP.port, sig, key, toSend);
                q.add(data);
                final Thread s = new Thread(send);
                s.start();
                while (q.size()!=0)
                    Thread.yield();
                TCP.Peer.RunnableSend.run = false;
            } else {
                System.err.println("Unhandled. arg="+toStart);
            }
        } else {
            System.err.println("Unhandled number of arguments. args="+args.length);
        }
    }

    private static final boolean isEquals(byte[] a, byte[] b, int length) {
        for (int i=0; i<length; i++)
            if (a[i] != b[i])
                return false;
        return true;
    }
}
