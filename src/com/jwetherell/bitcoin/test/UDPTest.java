package com.jwetherell.bitcoin.test;

import java.util.Queue;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.Listener;
import com.jwetherell.bitcoin.Receiver;
import com.jwetherell.bitcoin.data_model.Data;
import com.jwetherell.bitcoin.networking.UDP;

public class UDPTest {

    public static final String      SENDER      = "S";
    public static final String      RECEIVER    = "R";

    @Test
    public void test() throws InterruptedException {
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
                UDP.Peer.RunnableRecv.run = false;
                UDP.Peer.RunnableSend.run = false;
            }
        };

        // Start both the sender and receiver
        UDP.Peer.RunnableRecv recv = new UDP.Peer.RunnableRecv(listener);
        final Thread r = new Thread(recv);
        r.start();     

        final UDP.Peer.RunnableSend send = new UDP.Peer.RunnableSend();
        final Data data = new Data(recv.getHost(), recv.getPort(), toSend);
        send.getQueue().add(data);
        final Thread s = new Thread(send);
        s.start();

        // Wait for threads to finish
        r.join();
        s.join();
    }

    public static void main(String[] args) throws Exception {
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
                UDP.Peer.RunnableRecv.run = false;
            }
        };

        if (args.length==1) {
            String toStart = args[0];
            if (toStart.equals(RECEIVER)) {
                final Thread r = new Thread(new UDP.Peer.RunnableRecv(listener));
                r.start(); 
            } else if (toStart.equals(SENDER)) {
                final UDP.Peer.RunnableSend send = new UDP.Peer.RunnableSend();
                Queue<Data> q = send.getQueue();
                final Data data = new Data(UDP.LOCAL, UDP.port, toSend);
                q.add(data);
                final Thread s = new Thread(send);
                s.start();
                while (q.size()!=0)
                    Thread.yield();
                UDP.Peer.RunnableSend.run = false;
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
