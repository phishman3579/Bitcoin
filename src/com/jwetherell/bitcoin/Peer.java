package com.jwetherell.bitcoin;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

import com.jwetherell.bitcoin.data_model.Coin;
import com.jwetherell.bitcoin.data_model.Data;
import com.jwetherell.bitcoin.interfaces.Listener;
import com.jwetherell.bitcoin.interfaces.Receiver;
import com.jwetherell.bitcoin.networking.Multicast;
import com.jwetherell.bitcoin.networking.TCP;

/**
 * Class which handles lower level sending and receiving of messages.
 */
public abstract class Peer {

    private static final boolean                DEBUG           = Boolean.getBoolean("debug");

    private static final int                    HEADER_LENGTH   = 8;
    private static final String                 WHOIS_MSG       = "Who is  ";
    private static final String                 IAM_MSG         = "I am    ";
    private static final String                 COIN_MSG        = "Coin    ";
    private static final String                 COIN_ACK        = "Coin ACK";

    private static final int                    NAME_LENGTH     = 4;

    private static final byte[]                 NO_SIG          = new byte[0];

    private final TCP.Peer.RunnableSend         sendTcp         = new TCP.Peer.RunnableSend();
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
                if (DEBUG) 
                    System.out.println("Listener ("+name+") received '"+hdr+"' msg");
                if (hdr.equals(WHOIS_MSG)) {
                    handleWhois(data,d);
                } else if (hdr.equals(IAM_MSG)) {
                    String n = handleIam(data,d);
                    processPendingCoins(n);
                    processPendingCoinAcks(n);
                } else if (hdr.equals(COIN_MSG)) {
                    handleCoin(data,d);
                } else if (hdr.equals(COIN_ACK)) {
                    handleCoinAck(data,d);
                } else {
                    System.err.println("Cannot handle msg. hdr="+hdr);
                }
                d = recv.getQueue().poll();
            }
            Thread.yield();
        }
    };

    private final TCP.Peer.RunnableRecv         recvTcp         = new TCP.Peer.RunnableRecv(listener);
    private final Multicast.Peer.RunnableRecv   recvMulti       = new Multicast.Peer.RunnableRecv(listener);

    // Keep track of everyone's name -> ip+port
    private final Map<String,Data>              peers           = new ConcurrentHashMap<String,Data>();

    // Pending msgs
    private final Map<String,List<Coin>>        pendingCoins    = new ConcurrentHashMap<String,List<Coin>>();
    private final Map<String,List<Coin>>        pendingAcks     = new ConcurrentHashMap<String,List<Coin>>();

    private final Thread                        tcpSend;
    private final Thread                        tcpRecv;
    private final Thread                        multiSend;
    private final Thread                        multiRecv;
    private final Queue<Data>                   sendTcpQueue;
    private final Queue<Data>                   sendMultiQueue;

    protected final String                      name;

    protected Peer(String name) {
        this.name = name;

        // Senders

        tcpSend = new Thread(sendTcp);
        this.sendTcpQueue = sendTcp.getQueue();
        tcpSend.start();

        multiSend = new Thread(sendMulti);
        this.sendMultiQueue = sendMulti.getQueue();
        multiSend.start();

        // Receivers

        tcpRecv = new Thread(recvTcp);
        tcpRecv.start();

        multiRecv = new Thread(recvMulti);
        multiRecv.start();
    }

    public void shutdown() throws InterruptedException {
        TCP.Peer.RunnableSend.run = false;
        TCP.Peer.RunnableRecv.run = false;

        Multicast.Peer.RunnableSend.run = false;
        Multicast.Peer.RunnableRecv.run = false;

        // Senders

        multiSend.interrupt();
        multiSend.join();

        tcpSend.interrupt();
        tcpSend.join();

        // Receivers

        tcpRecv.interrupt();
        tcpRecv.join();

        multiRecv.interrupt();
        multiRecv.join();
    }

    /** Get encoded public key **/
    protected abstract byte[] getPublicKey();

    private void sendWhois(String name) {
        final byte[] msg = getWhoisMsg(name);
        final Data data = new Data(recvTcp.getHost(), recvTcp.getPort(), recvMulti.getHost(), recvMulti.getPort(), getPublicKey(), NO_SIG, msg);
        sendMultiQueue.add(data);
    }

    private void handleWhois(byte[] bytes, Data data) {
        final String name = parseWhoisMsg(bytes);

        // If your name then shout it out!
        if (name.equals(this.name))
            sendIam();
    }

    private void sendIam() {
        final byte[] msg = getIamMsg(name);
        final byte[] sig = signMsg(msg);
        final Data data = new Data(recvTcp.getHost(), recvTcp.getPort(), recvMulti.getHost(), recvMulti.getPort(), getPublicKey(), sig, msg);
        sendMultiQueue.add(data);
    }

    private String handleIam(byte[] bytes, Data data) {
        final boolean isVerified = verifyMsg(data.publicKey.array(), data.signature.array(), data.data.array());
        if (!isVerified) {
            System.err.println("handleIam() Data is NOT from a verified source. data="+data.toString());
            return null;
        }
        final String name = parseIamMsg(bytes);

        // Ignore your own iam msg
        if (name.equals(this.name))
            return name;

        // Add peer
        peers.put(name, data);

        return name;
    }

    /** Sign message with private key **/
    protected abstract byte[] signMsg(byte[] bytes);

    /** send coin to the peer named 'name' **/
    protected void sendCoin(String name, Coin coin) {
        final Data d = peers.get(name);
        if (d == null){
            // Could not find peer, broadcast a whois
            addPendingCoin(name,coin);
            sendWhois(name);
            return;
        }

        final byte[] msg = getCoinMsg(coin);
        final byte[] sig = signMsg(msg);
        final Data data = new Data(recvTcp.getHost(), recvTcp.getPort(), d.sourceAddr.getHostAddress(), d.sourcePort, getPublicKey(), sig, msg);
        sendTcpQueue.add(data);
    }

    private void handleCoin(byte[] bytes, Data data) {
        final boolean isVerified = verifyMsg(data.publicKey.array(), data.signature.array(), data.data.array());
        if (!isVerified) {
            System.err.println("handleCoin() Data is NOT from a verified source. data="+data.toString());
            return;
        }
        final Coin coin = parseCoinMsg(bytes);
        final String from = coin.from;

        // Let the app logic do what it needs to
        handleCoin(from,coin);

        // Send an ACK msg
        ackCoin(from, coin);
    }

    /** Verify the bytes given the public key and signature **/
    protected abstract boolean verifyMsg(byte[] publicKey, byte[] signature, byte[] bytes);

    /** What do you want to do now that you have received a coin **/
    protected abstract void handleCoin(String from, Coin coin);

    private void ackCoin(String name, Coin coin) {
        final Data d = peers.get(name);
        if (d == null){
            // Could not find peer, broadcast a whois
            addPendingCoinAck(name,coin);
            sendWhois(name);
            return;
        }

        final byte[] msg = getCoinAck(coin);
        final byte[] sig = signMsg(msg);
        final Data data = new Data(recvTcp.getHost(), recvTcp.getPort(), d.sourceAddr.getHostAddress(), d.sourcePort, getPublicKey(), sig, msg);
        sendTcpQueue.add(data);
    }

    private void handleCoinAck(byte[] bytes, Data data) {
        final boolean isVerified = verifyMsg(data.publicKey.array(), data.signature.array(), data.data.array());
        if (!isVerified) {
            System.err.println("handleCoinAck() Data is NOT from a verified source. data="+data.toString());
            return;
        }
        final Coin coin = parseCoinAck(bytes);

        // Let the app logic do what it needs to
        handleCoinAck(coin);
    }

    /** What do you want to do now that you received an ACK for a sent coin **/
    protected abstract void handleCoinAck(Coin coin);

    private void addPendingCoin(String n, Coin c) {
        List<Coin> l = pendingCoins.get(n);
        if (l == null) {
            l = new LinkedList<Coin>();
            pendingCoins.put(n, l);
        }
        l.add(c);
    }

    private void processPendingCoins(String n) {
        List<Coin> l = pendingCoins.get(n);
        if (l==null || l.size()==0)
            return;
        while (l.size()>0) {
            final Coin c = l.remove(0);
            final Data d = peers.get(n);
            if (d == null)
                return;
            final byte[] msg = getCoinMsg(c);
            final byte[] sig = signMsg(msg);
            final Data data = new Data(recvTcp.getHost(), recvTcp.getPort(), d.sourceAddr.getHostAddress(), d.sourcePort, getPublicKey(), sig, msg);
            sendTcpQueue.add(data);
        }
    }

    private void addPendingCoinAck(String n, Coin c) {
        List<Coin> l = pendingAcks.get(n);
        if (l == null) {
            l = new LinkedList<Coin>();
            pendingAcks.put(n, l);
        }
        l.add(c);
    }

    private void processPendingCoinAcks(String n) {
        List<Coin> l = pendingAcks.get(n);
        if (l==null || l.size()==0)
            return;
        while (l.size()>0) {
            final Coin c = l.remove(0);
            final Data d = peers.get(n);
            if (d == null)
                return;
            final byte[] msg = getCoinAck(c);
            final byte[] sig = signMsg(msg);
            final Data data = new Data(recvTcp.getHost(), recvTcp.getPort(), d.sourceAddr.getHostAddress(), d.sourcePort, getPublicKey(), sig, msg);
            sendTcpQueue.add(data);
        }
    }

    public static final byte[] getIamMsg(String name) {
        final byte[] bName = name.getBytes();
        final int nameLength = bName.length;
        final byte[] msg = new byte[HEADER_LENGTH + NAME_LENGTH + nameLength];
        final ByteBuffer buffer = ByteBuffer.wrap(msg);

        buffer.put(IAM_MSG.getBytes());
        buffer.putInt(nameLength);
        buffer.put(bName);

        return msg;
    }

    public static final String parseIamMsg(byte[] bytes) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);

        final byte [] bMsgType = new byte[HEADER_LENGTH];
        buffer.get(bMsgType);

        final int nLength = buffer.getInt();
        final byte [] bName = new byte[nLength];
        buffer.get(bName);

        return new String(bName);
    }

    public static final byte[] getWhoisMsg(String name) {
        final byte[] bName = name.getBytes();
        final int nameLength = bName.length;
        final byte[] msg = new byte[HEADER_LENGTH + NAME_LENGTH + nameLength];
        final ByteBuffer buffer = ByteBuffer.wrap(msg);

        buffer.put(WHOIS_MSG.getBytes());
        buffer.putInt(nameLength);
        buffer.put(bName);

        return msg;
    }

    public static final String parseWhoisMsg(byte[] bytes) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);

        final byte [] bMsgType = new byte[HEADER_LENGTH];
        buffer.get(bMsgType);

        final int nLength = buffer.getInt();
        final byte [] bName = new byte[nLength];
        buffer.get(bName);

        return new String(bName);
    }

    public static final byte[] getCoinMsg(Coin coin) {
        final byte[] msg = new byte[HEADER_LENGTH + coin.getBufferLength()];
        final ByteBuffer coinBuffer = ByteBuffer.allocate(coin.getBufferLength());
        coin.toBuffer(coinBuffer);
        final ByteBuffer buffer = ByteBuffer.wrap(msg);
        buffer.put(COIN_MSG.getBytes());

        buffer.put(coinBuffer);

        return msg;
    }

    public static final Coin parseCoinMsg(byte[] bytes) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        final byte [] bMsgType = new byte[HEADER_LENGTH];
        buffer.get(bMsgType);

        final Coin coin = new Coin();
        coin.fromBuffer(buffer);

        return coin;
    }

    public static final byte[] getCoinAck(Coin coin) {
        final byte[] msg = new byte[HEADER_LENGTH + coin.getBufferLength()];
        final ByteBuffer coinBuffer = ByteBuffer.allocate(coin.getBufferLength());
        coin.toBuffer(coinBuffer);
        final ByteBuffer buffer = ByteBuffer.wrap(msg);
        buffer.put(COIN_ACK.getBytes());

        buffer.put(coinBuffer);

        return msg;
    }

    public static final Coin parseCoinAck(byte[] bytes) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        final byte [] bMsgType = new byte[HEADER_LENGTH];
        buffer.get(bMsgType);

        final Coin coin = new Coin();
        coin.fromBuffer(buffer);

        return coin;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("name=").append(name);
        return builder.toString();
    }
}
