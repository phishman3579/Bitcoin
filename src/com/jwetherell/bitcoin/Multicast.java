package com.jwetherell.bitcoin;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

public class Multicast {

    // Which port should we listen to
    public static final int         PORT        = 5000;
    // Which address
    public static final String      GROUP       = "225.4.5.6";

    public static MulticastSocket createReceiver() throws IOException {
        // Create the socket and bind it to port 'port'.
        MulticastSocket s = new MulticastSocket(PORT);
        // join the multicast group
        s.joinGroup(InetAddress.getByName(GROUP));
        // Now the socket is set up and we are ready to receive packets
        return s;
    }

    public static void destoryReceiver(MulticastSocket s) throws UnknownHostException, IOException {
        // Leave the multicast group and close the socket
        s.leaveGroup(InetAddress.getByName(GROUP));
        s.close();
    }

    public static void recvData(MulticastSocket s, byte[] buffer) throws IOException {
        // Create a DatagramPacket and do a receive
        DatagramPacket pack = new DatagramPacket(buffer, buffer.length);
        s.receive(pack);
        // We have finished receiving data
    }

    public static MulticastSocket createSender() throws IOException {
        // Create the socket but we don't bind it as we are only going to send data
        MulticastSocket s = new MulticastSocket();
        // Note that we don't have to join the multicast group if we are only
        // sending data and not receiving
        return s;
    }

    public static void destroySender(MulticastSocket s) throws IOException {
        // When we have finished sending data close the socket
        s.close();
    }

    public static void sendData(MulticastSocket s, int ourTTL, byte[] buffer) throws IOException {
        // Create a DatagramPacket 
        DatagramPacket pack = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(GROUP), PORT);
        // Get the current TTL, set our TTL, do a send, reset the TTL  
        int ttl = s.getTimeToLive(); 
        s.setTimeToLive(ourTTL); 
        s.send(pack); 
        s.setTimeToLive(ttl);
    }

    public static final class Peer {

        public static boolean       run     = false;

        // TTL for send
        private static final int    ttl     = 10;

        private Peer() { }

        public static final class RunnableRecv implements Runnable {

            public static boolean   run         =   true;

            private final Listener  listener;
            private final byte[]    toRecv;

            public RunnableRecv(Listener listener, byte[] toRecv) {
                this.listener = listener;
                this.toRecv = toRecv;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void run() {
                MulticastSocket r = null;
                try {
                    System.out.println("Creating receiver");
                    r = Multicast.createReceiver();
                    while (run) {
                        Multicast.recvData(r,toRecv);
                        listener.onMessage(toRecv);
                        Thread.yield();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        Multicast.destoryReceiver(r);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        public static final class RunnableSend implements Runnable {

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
                    System.out.println("Creating sender");
                    s = Multicast.createSender();
                    Multicast.sendData(s,ttl,toSend);
                    System.out.println("Sender sending '"+new String(toSend)+"'");
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        Multicast.destroySender(s);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
    }
}
