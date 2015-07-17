package com.jwetherell.bitcoin.networking;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.jwetherell.bitcoin.data_model.Data;
import com.jwetherell.bitcoin.interfaces.Listener;
import com.jwetherell.bitcoin.interfaces.Receiver;
import com.jwetherell.bitcoin.interfaces.Sender;

public class TCP {

    private static final boolean    DEBUG       = Boolean.getBoolean("debug");

    public static final String      LOCAL       = "127.0.0.1";

    public static int               port        = 2221;

    public static ServerSocket createServer(int port) throws IOException {
        final ServerSocket serverSocket = new ServerSocket(port);
        return serverSocket;
    }

    public static void destoryServer(ServerSocket s) throws IOException {
        if (s != null)
            s.close();
    }

    public static Socket createClient(String host, int port) throws IOException {
        final Socket outgoingSocket = new Socket(host, port);
        return outgoingSocket;
    }

    public static void destoryClient(Socket s) throws IOException {
        if (s != null)
            s.close();
    }

    public static void sendData(Socket socket, byte[] buffer) throws IOException {
        final OutputStream out = socket.getOutputStream(); 
        final DataOutputStream dos = new DataOutputStream(out);
        dos.write(buffer);
        dos.flush();
        dos.close();
        out.flush();
        out.close();
    }

    /**
     * Blocking call
     */
    public static boolean recvData(ServerSocket serverSocket, byte[] buffer) throws IOException {
        serverSocket.setSoTimeout(10);
        Socket incomingSocket = null;
        try {
            incomingSocket = serverSocket.accept();
        } catch (SocketTimeoutException e) {
            return false;
        }
        final InputStream input = incomingSocket.getInputStream();
        input.read(buffer);
        input.close();
        return true;
    }

    public static final class Peer { 

        private static final int        BUFFER_SIZE     = 1*1024;

        private Peer() { }

        public static final class RunnableRecv implements Runnable, Receiver {

            public static volatile boolean                      run         = true;

            private final ConcurrentLinkedQueue<Data>           toRecv      = new ConcurrentLinkedQueue<Data>();
            private final int                                   port;
            private final Listener                              listener;

            private volatile boolean                            isReady     = false;

            public RunnableRecv(Listener listener) {
                run = true;
                this.port = TCP.port++;
                this.listener = listener;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Queue<Data> getQueue() {
                return toRecv;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isReady() {
                return isReady;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getHost() {
                return LOCAL;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public int getPort() {
                return port;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void run() {
                ServerSocket s = null;
                try {
                    if (DEBUG) 
                        System.out.println("Creating server. port="+port);
                    s = TCP.createServer(port);
                    isReady = true;
                    while (run) {
                        final ByteBuffer b = ByteBuffer.allocate(BUFFER_SIZE);
                        final boolean p = TCP.recvData(s,b.array());
                        if (!p) {
                            Thread.yield();
                            continue;
                        }

                        final Data data = new Data();
                        data.fromBuffer(b);

                        if (DEBUG) 
                            System.out.println("Server ("+getHost()+":"+getPort()+") received '"+new String(data.message.array())+"' from "+data.sourceAddr.getHostAddress()+":"+data.sourcePort);

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
                        TCP.destoryServer(s);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        public static final class RunnableSend implements Runnable, Sender {

            public static volatile boolean                      run         = true;

            private final ConcurrentLinkedQueue<Data>           toSend      = new ConcurrentLinkedQueue<Data>();

            private volatile boolean                            isReady     = false;

            public RunnableSend() {
                run = true;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Queue<Data> getQueue() {
                return toSend;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isReady() {
                return isReady;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void run() {
                Socket s = null;
                try {
                    isReady = true;
                    while (run) {
                        if (DEBUG && toSend.size()>1)
                            System.out.println("Client toSend size="+toSend.size());
                        final Data d = toSend.poll();
                        if (d != null) {
                            final byte[] buffer = new byte[BUFFER_SIZE];
                            final ByteBuffer bytes = ByteBuffer.wrap(buffer);
                            d.toBuffer(bytes);

                            if (DEBUG) 
                                System.out.println("Client ("+d.sourceAddr.getHostAddress()+":"+d.sourcePort+") sending '"+new String(d.message.array())+"'");

                            s = TCP.createClient(d.destAddr.getHostAddress(), d.destPort);
                            TCP.sendData(s, buffer);
                        }
                        Thread.yield();
                    }
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally { 
                    try {
                        TCP.destoryClient(s);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
    }
}
