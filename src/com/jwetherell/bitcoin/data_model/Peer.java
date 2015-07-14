package com.jwetherell.bitcoin.data_model;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

import com.jwetherell.bitcoin.Listener;
import com.jwetherell.bitcoin.Receiver;
import com.jwetherell.bitcoin.networking.Multicast;
import com.jwetherell.bitcoin.networking.UDP;

public class Peer {

    private static final int                    HEADER_LENGTH   = 5;
    private static final String                 HELLO_MSG       = "Hello";
    private static final String                 COIN_MSG        = "Coin ";
    private static final String                 WHOIS_MSG       = "Whois";

    private static final int                    NAME_LENGTH     = 4;

    private final UDP.Peer.RunnableSend         sendUdp         = new UDP.Peer.RunnableSend();
    private final Multicast.Peer.RunnableSend   sendMulti       = new Multicast.Peer.RunnableSend();

    private final Listener                      listener        = new Listener() {
        /**
         * {@inheritDoc}
         */
        @Override
        public void onMessage(Receiver recv) {
            Data d = recv.getQueue().poll();
            while (d != null) {
                final byte[] data = d.data.array();
                final String string = new String(data);
                String hdr = string.substring(0, HEADER_LENGTH);
                String body = string.substring(HEADER_LENGTH, data.length);
                System.out.println("Listener ("+name+") received '"+body+"'");
                if (hdr.equals(HELLO_MSG)) {
                    parseHello(body.getBytes(),d);
                } else if (hdr.equals(WHOIS_MSG)) {
                    parseWhois(body.getBytes(),d);
                } else if (hdr.equals(COIN_MSG)) {
                    parseCoin(body.getBytes());
                } else {
                    System.err.println("Cannot handle msg. hdr="+hdr+" body="+body);
                }
                d = recv.getQueue().poll();
            }
            Thread.yield();
        }
    };

    private final UDP.Peer.RunnableRecv         recvUdp         = new UDP.Peer.RunnableRecv(listener);
    private final Multicast.Peer.RunnableRecv   recvMulti       = new Multicast.Peer.RunnableRecv(listener);
    private final Map<String,Data>              peers           = new ConcurrentHashMap<String,Data>();

    private final String                        name;
    private final Wallet                        wallet;
    private final Thread                        udpSend;
    private final Thread                        udpRecv;
    private final Thread                        multiSend;
    private final Thread                        multiRecv;
    private final Queue<Data>                   sendUdpQueue;
    private final Queue<Data>                   sendMultiQueue;

    public Peer(String name) {
        this.name = name;
        this.wallet = new Wallet(name);

        udpSend = new Thread(sendUdp);
        this.sendUdpQueue = sendUdp.getQueue();
        udpSend.start();

        multiSend = new Thread(sendMulti);
        this.sendMultiQueue = sendMulti.getQueue();
        multiSend.start();

        udpRecv = new Thread(recvUdp);
        udpRecv.start();

        multiRecv = new Thread(recvMulti);
        multiRecv.start();

        sendHello();
    }

    public void shutdown() throws InterruptedException {
        UDP.Peer.RunnableSend.run = false;
        UDP.Peer.RunnableRecv.run = false;
        Multicast.Peer.RunnableSend.run = false;
        Multicast.Peer.RunnableRecv.run = false;

        udpSend.interrupt();
        udpSend.join();

        udpRecv.interrupt();
        udpRecv.join();
        
        multiSend.interrupt();
        multiSend.join();
        
        multiRecv.interrupt();
        multiRecv.join();
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Map<String,Data> getPeers() {
        return peers;
    }

    private void sendHello() {
        final byte[] bMsgType = HELLO_MSG.getBytes();
        final byte[] bName = name.getBytes();
        final ByteBuffer nameLength = ByteBuffer.allocate(NAME_LENGTH);
        nameLength.putInt(bName.length);

        final byte[] msg = new byte[bMsgType.length + NAME_LENGTH + bName.length];
        int pos = 0;
        System.arraycopy(bMsgType, 0, msg, pos, bMsgType.length);
        pos += bMsgType.length;
        System.arraycopy(nameLength.array(), 0, msg, pos, NAME_LENGTH);
        pos += NAME_LENGTH;
        System.arraycopy(bName, 0, msg, pos, bName.length);

        final Data data = new Data(recvUdp.getHost(), recvUdp.getPort(), msg);
        sendMultiQueue.add(data);
    }

    private void parseHello(byte[] bytes, Data data) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        final int nLength = buffer.getInt();
        final byte [] bName = new byte[nLength];
        buffer.get(bName);
        final String name = new String(bName);

        // Ignore your own hello msg
        if (name.equals(this.name))
            return;

        // Add peer
        peers.put(name, data);
    }

    private void sendWhois(String name) {
        final byte[] bMsgType = WHOIS_MSG.getBytes();
        final byte[] bName = name.getBytes();
        final ByteBuffer nameLength = ByteBuffer.allocate(NAME_LENGTH);
        nameLength.putInt(bName.length);

        final byte[] msg = new byte[bMsgType.length + NAME_LENGTH + bName.length];
        int pos = 0;
        System.arraycopy(bMsgType, 0, msg, pos, bMsgType.length);
        pos += bMsgType.length;
        System.arraycopy(nameLength.array(), 0, msg, pos, NAME_LENGTH);
        pos += NAME_LENGTH;
        System.arraycopy(bName, 0, msg, pos, bName.length);

        final Data data = new Data(recvUdp.getHost(), recvUdp.getPort(), msg);
        sendMultiQueue.add(data);
    }

    private void parseWhois(byte[] bytes, Data data) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        final int nLength = buffer.getInt();
        final byte [] bName = new byte[nLength];
        buffer.get(bName);
        final String name = new String(bName);

        // If your name then shout it out!
        if (name.equals(this.name))
            sendHello();
    }

    public boolean sendCoin(String name, int value) {
        final Data d = peers.get(name);
        if (d == null){
            // Could not find peer, broadcast a whois
            sendWhois(name);
            return false;
        }

        final byte[] bMsgType = COIN_MSG.getBytes();
        final Coin coin = this.wallet.removeCoin(value);
        final byte[] coinBytes = coin.toBytes();

        final byte[] msg = new byte[bMsgType.length + coinBytes.length];
        int pos = 0;
        System.arraycopy(bMsgType, 0, msg, pos, bMsgType.length);
        pos += bMsgType.length;
        System.arraycopy(coinBytes, 0, msg, pos, coinBytes.length);

        final Data data = new Data(d.addr.getHostAddress(), d.port, msg);
        sendUdpQueue.add(data);

        return true;
    }

    private void parseCoin(byte[] bytes) {
        final Coin coin = new Coin();
        coin.fromBytes(bytes);
        wallet.addCoin(coin);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("name=").append(name).append(" ").append(wallet.toString());
        return builder.toString();
    }
}
