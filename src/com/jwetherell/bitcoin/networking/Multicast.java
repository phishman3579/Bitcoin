package com.jwetherell.bitcoin.networking;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.jwetherell.bitcoin.Listener;
import com.jwetherell.bitcoin.Receiver;
import com.jwetherell.bitcoin.Sender;
import com.jwetherell.bitcoin.data_model.Data;

public class Multicast {

    public static final int         PORT        = 5000;
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

    public static DatagramPacket recvData(MulticastSocket s, byte[] buffer) throws IOException {
        s.setSoTimeout(100);
        // Create a DatagramPacket and do a receive
        DatagramPacket pack = new DatagramPacket(buffer, buffer.length);
        try {
            s.receive(pack);
        } catch (SocketTimeoutException e) {
            return null;
        }
        // We have finished receiving data
        return pack;
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

        private static final int BUFFER_SIZE = 64*1000;

        public static final class RunnableRecv implements Runnable, Receiver {

            public static boolean                               run         =   true;

            private final ConcurrentLinkedQueue<Data>           toRecv      = new ConcurrentLinkedQueue<Data>();
            private final Listener                              listener;

            public RunnableRecv(Listener listener) {
                run = true;
                this.listener = listener;
            }

            public Queue<Data> getQueue() {
                return toRecv;
            }

            public String getHost() {
                return GROUP;
            }

            public int getPort() {
                return PORT;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void run() {
                MulticastSocket s = null;
                try {
                    System.out.println("Creating receiver");
                    s = Multicast.createReceiver();
                    while (run) {
                        final ByteBuffer b = ByteBuffer.allocate(BUFFER_SIZE);
                        DatagramPacket p = Multicast.recvData(s, b.array());
                        if (p == null) {
                            Thread.yield();
                            continue;
                        }

                        final Data data = new Data();
                        data.fromBuffer(b);

                        System.out.println("Server received '"+new String(data.data.array())+"' from "+data.addr.getHostAddress()+":"+data.port);

                        toRecv.add(data);
                        listener.onMessage(this);

                        Thread.yield();
                    }
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        Multicast.destoryReceiver(s);
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        public static final class RunnableSend implements Runnable, Sender {

            private static final int                      ttl         = 1;

            public static boolean                         run         = true;

            public final ConcurrentLinkedQueue<Data>      toSend      = new ConcurrentLinkedQueue<Data>();

            public RunnableSend() {
                run = true;
            }

            public Queue<Data> getQueue() {
                return toSend;
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
                    while (run) {
                        if (toSend.size()>1)
                            System.out.println("Client toSend size="+toSend.size());
                        final Data d = toSend.poll();
                        if (d != null) {
                            final byte[] buffer = new byte[BUFFER_SIZE];
                            final ByteBuffer bytes = ByteBuffer.wrap(buffer);
                            d.toBuffer(bytes);

                            System.out.println("Client ("+d.addr.getHostAddress()+":"+d.port+") sending '"+new String(d.data.array())+"'");

                            Multicast.sendData(s, ttl, buffer);
                        }
                        Thread.yield();
                    }
                } catch (SocketException e) {
                    e.printStackTrace();
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
