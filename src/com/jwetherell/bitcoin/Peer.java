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
                    System.out.println("Listener ("+myName+") received '"+hdr+"' msg");
                if (hdr.equals(WHOIS_MSG)) {
                    handleWhois(data,d);
                } else if (hdr.equals(IAM_MSG)) {
                    String n = handleIam(data,d);
                    processCoinsToSend(n);
                    processCoinAcksToSend(n);
                    processCoinsToRecv(n);
                    processCoinAcksToRecv(n);
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
    // Keep track of everyone's name -> public key
    private final Map<String,ByteBuffer>        publicKeys      = new ConcurrentHashMap<String,ByteBuffer>();

    // Pending msgs
    private final Map<String,List<Coin>>        coinsToSend     = new ConcurrentHashMap<String,List<Coin>>();
    private final Map<String,List<Coin>>        coinAcksToSend  = new ConcurrentHashMap<String,List<Coin>>();
    private final Map<String,List<Queued>>      coinsToRecv     = new ConcurrentHashMap<String,List<Queued>>();
    private final Map<String,List<Queued>>      coinAcksToRecv  = new ConcurrentHashMap<String,List<Queued>>();

    private final Thread                        tcpSend;
    private final Thread                        tcpRecv;
    private final Thread                        multiSend;
    private final Thread                        multiRecv;
    private final Queue<Data>                   sendTcpQueue;
    private final Queue<Data>                   sendMultiQueue;

    protected final String                      myName;

    protected Peer(String name) {
        this.myName = name;

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

    private void sendWhois(String who) {
        final byte[] msg = getWhoisMsg(who);
        final Data data = new Data(recvTcp.getHost(), recvTcp.getPort(), recvMulti.getHost(), recvMulti.getPort(), NO_SIG, msg);
        sendMultiQueue.add(data);
    }

    private void handleWhois(byte[] bytes, Data data) {
        final String name = parseWhoisMsg(bytes);

        // If your name then shout it out!
        if (name.equals(this.myName))
            sendIam();
    }

    private void sendIam() {
        final byte[] msg = getIamMsg(myName, getPublicKey());
        final Data data = new Data(recvTcp.getHost(), recvTcp.getPort(), recvMulti.getHost(), recvMulti.getPort(), NO_SIG, msg);
        sendMultiQueue.add(data);
    }

    private String handleIam(byte[] bytes, Data data) {
        final String name = parseIamMsgForName(bytes);
        final byte[] key = parseIamMsgForKey(bytes);

        // Ignore your own iam msg
        if (name.equals(this.myName))
            return name;

        // Add peer
        peers.put(name, data);
        final byte[] copy = new byte[key.length];
        System.arraycopy(key, 0, copy, 0, key.length);
        publicKeys.put(name, ByteBuffer.wrap(copy));

        return name;
    }

    /** Sign message with private key **/
    protected abstract byte[] signMsg(byte[] bytes);

    /** send coin to the peer named 'to' **/
    protected void sendCoin(String to, Coin coin) {
        final Data d = peers.get(to);
        if (d == null){
            // Could not find peer, broadcast a whois
            addCoinToSend(to,coin);
            sendWhois(to);
            return;
        }

        final byte[] msg = getCoinMsg(coin);
        final byte[] sig = signMsg(msg);
        final Data data = new Data(recvTcp.getHost(), recvTcp.getPort(), d.sourceAddr.getHostAddress(), d.sourcePort, sig, msg);
        sendTcpQueue.add(data);
    }

    private void handleCoin(byte[] bytes, Data data) {
        final Coin coin = parseCoinMsg(bytes);
        final String from = coin.from;
        handleCoin(from, coin, data);
    }

    private void handleCoin(String from, Coin coin, Data data) {
        if (!publicKeys.containsKey(from)) {
            addCoinToRecv(from, coin, data);
            sendWhois(from);
            return;
        }

        final byte[] key = publicKeys.get(from).array();
        if (!verifyMsg(key, data.signature.array(), data.data.array())) {
            System.out.println("handleCoin() coin NOT verified. data="+data.toString());
            return;
        }

        // Let the app logic do what it needs to
        handleCoin(from,coin);

        // Send an ACK msg
        ackCoin(from, coin);
    }

    /** Verify the bytes given the public key and signature **/
    protected abstract boolean verifyMsg(byte[] publicKey, byte[] signature, byte[] bytes);

    /** What do you want to do now that you have received a coin **/
    protected abstract void handleCoin(String from, Coin coin);

    private void ackCoin(String to, Coin coin) {
        final Data d = peers.get(to);
        if (d == null){
            // Could not find peer, broadcast a whois
            addCoinAckToSend(to,coin);
            sendWhois(to);
            return;
        }

        final byte[] msg = getCoinAck(coin);
        final byte[] sig = signMsg(msg);
        final Data data = new Data(recvTcp.getHost(), recvTcp.getPort(), d.sourceAddr.getHostAddress(), d.sourcePort, sig, msg);
        sendTcpQueue.add(data);
    }

    private void handleCoinAck(byte[] bytes, Data data) {
        final Coin coin = parseCoinAck(bytes);
        final String to = coin.to; // yes, use the to field.
        handleCoinAck(to, coin, data);
    }

    private void handleCoinAck(String from, Coin coin, Data data) {
        if (!publicKeys.containsKey(from)) {
            addCoinAckToRecv(from, coin, data);
            sendWhois(from);
            return;
        }

        final byte[] key = publicKeys.get(from).array();
        if (!verifyMsg(key, data.signature.array(), data.data.array())) {
            System.out.println("handleCoinAck() coin NOT verified. data="+data.toString());
            return;
        }

        // Let the app logic do what it needs to
        handleCoinAck(coin);
    }

    /** What do you want to do now that you received an ACK for a sent coin **/
    protected abstract void handleCoinAck(Coin coin);

    private void addCoinToSend(String to, Coin c) {
        List<Coin> l = coinsToSend.get(to);
        if (l == null) {
            l = new LinkedList<Coin>();
            coinsToSend.put(to, l);
        }
        l.add(c);
    }

    private void processCoinsToSend(String to) {
        List<Coin> l = coinsToSend.get(to);
        if (l==null || l.size()==0)
            return;
        while (l.size()>0) {
            final Coin c = l.remove(0);
            final Data d = peers.get(to);
            final byte[] msg = getCoinMsg(c);
            final byte[] sig = signMsg(msg);
            final Data data = new Data(recvTcp.getHost(), recvTcp.getPort(), d.sourceAddr.getHostAddress(), d.sourcePort, sig, msg);
            sendTcpQueue.add(data);
        }
    }

    private void addCoinAckToSend(String to, Coin c) {
        List<Coin> l = coinAcksToSend.get(to);
        if (l == null) {
            l = new LinkedList<Coin>();
            coinAcksToSend.put(to, l);
        }
        l.add(c);
    }

    private void processCoinAcksToSend(String to) {
        List<Coin> l = coinAcksToSend.get(to);
        if (l==null || l.size()==0)
            return;
        while (l.size()>0) {
            final Coin c = l.remove(0);
            final Data d = peers.get(to);
            final byte[] msg = getCoinAck(c);
            final byte[] sig = signMsg(msg);
            final Data data = new Data(recvTcp.getHost(), recvTcp.getPort(), d.sourceAddr.getHostAddress(), d.sourcePort, sig, msg);
            sendTcpQueue.add(data);
        }
    }

    private void addCoinToRecv(String from, Coin c, Data d) {
        final Queued q = new Queued(c,d);
        List<Queued> lc = coinsToRecv.get(from);
        if (lc == null) {
            lc = new LinkedList<Queued>();
            coinsToRecv.put(from, lc);
        }
        lc.add(q);
    }

    private void processCoinsToRecv(String from) {
        List<Queued> l = coinsToRecv.get(from);
        if (l==null || l.size()==0)
            return;
        while (l.size()>0) {
            final Queued q = l.remove(0);
            handleCoin(from, q.coin, q.data);
        }
    }

    private void addCoinAckToRecv(String from, Coin c, Data d) {
        final Queued q = new Queued(c,d);
        List<Queued> lc = coinAcksToRecv.get(from);
        if (lc == null) {
            lc = new LinkedList<Queued>();
            coinAcksToRecv.put(from, lc);
        }
        lc.add(q);
    }

    private void processCoinAcksToRecv(String from) {
        List<Queued> l = coinAcksToRecv.get(from);
        if (l==null || l.size()==0)
            return;
        while (l.size()>0) {
            final Queued q = l.remove(0);
            handleCoinAck(from, q.coin, q.data);
        }
    }

    private static final class Queued {

        private final Coin coin;
        private final Data data;

        private Queued(Coin coin, Data data) {
            this.coin = coin;
            this.data = data;
        }
    }

    public static final byte[] getIamMsg(String name, byte[] publicKey) {
        final byte[] bName = name.getBytes();
        final int nameLength = bName.length;
        final int publicKeyLength = publicKey.length;
        final byte[] msg = new byte[HEADER_LENGTH + NAME_LENGTH + nameLength + NAME_LENGTH + publicKeyLength];
        final ByteBuffer buffer = ByteBuffer.wrap(msg);

        buffer.put(IAM_MSG.getBytes());

        buffer.putInt(nameLength);
        buffer.put(bName);

        buffer.putInt(publicKeyLength);
        buffer.put(publicKey);

        return msg;
    }

    public static final String parseIamMsgForName(byte[] bytes) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);

        final byte [] bMsgType = new byte[HEADER_LENGTH];
        buffer.get(bMsgType);

        final int nLength = buffer.getInt();
        final byte [] bName = new byte[nLength];
        buffer.get(bName);

        final int kLength = buffer.getInt();
        final byte [] bKey = new byte[kLength];
        buffer.get(bKey);

        return new String(bName);
    }

    public static final byte[] parseIamMsgForKey(byte[] bytes) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);

        final byte [] bMsgType = new byte[HEADER_LENGTH];
        buffer.get(bMsgType);

        final int nLength = buffer.getInt();
        final byte [] bName = new byte[nLength];
        buffer.get(bName);

        final int kLength = buffer.getInt();
        final byte [] bKey = new byte[kLength];
        buffer.get(bKey);

        return bKey;
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
        builder.append("name=").append(myName);
        return builder.toString();
    }
}
