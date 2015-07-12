package com.jwetherell.bitcoin;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class UDP {

    public static final int PORT = 9876;

    public static DatagramSocket createServer() throws SocketException {
        DatagramSocket serverSocket = new DatagramSocket(PORT);
        return serverSocket;
    }

    public static DatagramSocket createClient() throws SocketException {
        DatagramSocket clientSocket = new DatagramSocket();
        return clientSocket;
    }

    public static void sendData(DatagramSocket socket, InetAddress IPAddress, int port, byte[] buffer) throws IOException {
        DatagramPacket sendPacket = new DatagramPacket(buffer, buffer.length, IPAddress, port);
        socket.send(sendPacket);
    }

    public static void recvData(DatagramSocket socket, byte[] buffer) throws IOException {
        DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
        socket.receive(receivePacket);
    }

    public static final class Peer { 

        private static InetAddress      addr        = null;

        private Peer() { }

        static {
            try {
                addr = InetAddress.getByName("127.0.0.1");
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }

        public static final class RunnableRecv implements Runnable {

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
                DatagramSocket r = null;
                try {
                    System.out.println("Creating server");
                    r = UDP.createServer();
                    UDP.recvData(r,toRecv);
                    listener.onMessage(toRecv);
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
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
                DatagramSocket s = null;
                try {
                    System.out.println("Creating client");
                    s = UDP.createClient();
                    System.out.println("Client sending '"+new String(toSend)+"'");
                    UDP.sendData(s,addr,UDP.PORT,toSend);
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
    }
}
