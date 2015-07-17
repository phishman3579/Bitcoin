package com.jwetherell.bitcoin;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.jwetherell.bitcoin.data_model.BlockChain.HashStatus;
import com.jwetherell.bitcoin.data_model.Coin;
import com.jwetherell.bitcoin.data_model.Data;
import com.jwetherell.bitcoin.data_model.Transaction;
import com.jwetherell.bitcoin.interfaces.Listener;
import com.jwetherell.bitcoin.interfaces.Receiver;
import com.jwetherell.bitcoin.networking.Multicast;
import com.jwetherell.bitcoin.networking.TCP;

/**
 * Class which handles lower level sending and receiving of messages.
 * 
 * Thread-Safe (Hopefully)
 */
public abstract class Peer {

    protected static enum KeyStatus { NO_PUBLIC_KEY, BAD_SIGNATURE, SUCCESS };

    protected static final boolean                DEBUG                   = Boolean.getBoolean("debug");

    private static final int                      HEADER_LENGTH           = 12;
    private static final String                   WHOIS_MSG               = "Who is      ";
    private static final String                   IAM_MSG                 = "I am        ";
    private static final String                   COIN_MSG                = "Coin        ";
    private static final String                   COIN_ACK                = "Coin ACK    ";
    private static final String                   TRANSACTION             = "Transaction ";
    private static final String                   VALIDATION              = "Validate    ";

    private static final int                      KEY_LENGTH              = 4;
    private static final int                      NAME_LENGTH             = 4;

    private static final String                   EVERY_ONE               = "EVERYONE";
    private static final byte[]                   NO_SIG                  = new byte[0];

    private final TCP.Peer.RunnableSend           runnableSendTcp         = new TCP.Peer.RunnableSend();
    private final Multicast.Peer.RunnableSend     runnableSendMulti       = new Multicast.Peer.RunnableSend();

    private final Listener                        listener                = new Listener() {
        /**
         * {@inheritDoc}
         */
        @Override
        public void onMessage(Receiver recv) {
            Data data = recv.getQueue().poll();
            while (data != null) {
                final String from = data.from;

                // Update peers
                peers.put(from, data);

                final byte[] bytes = data.message.array();
                final String string = new String(bytes);
                final String hdr = string.substring(0, HEADER_LENGTH);
                if (DEBUG) 
                    System.out.println("Listener ("+myName+") received '"+hdr+"' msg");
                if (hdr.equals(WHOIS_MSG)) {
                    handleWhois(bytes,data);
                } else if (hdr.equals(IAM_MSG)) {
                    handleIam(bytes,data);
                    processCoinsToSend(from);
                    processCoinsToRecv(from);
                } else if (hdr.equals(COIN_MSG)) {
                    handleCoin(bytes,data);
                } else if (hdr.equals(COIN_ACK)) {
                    handleCoinAck(bytes,data);
                } else if (hdr.equals(TRANSACTION)) {
                    handleTransaction(bytes,data);
                } else if (hdr.equals(VALIDATION)) {
                    handleValidation(bytes,data);
                } else {
                    System.err.println("Cannot handle msg. hdr="+hdr);
                }

                // Get next message
                data = recv.getQueue().poll();
            }
            Thread.yield();
        }
    };

    // Keep track of everyone's name -> ip+port
    private final Map<String,Data>                peers                   = new ConcurrentHashMap<String,Data>();

    // Pending msgs (happens if we don't know the ip+port OR the public key of a host
    private final Map<String,Queue<Queued>>        coinsToSend            = new ConcurrentHashMap<String,Queue<Queued>>();
    private final Map<String,Queue<Queued>>        coinsToRecv            = new ConcurrentHashMap<String,Queue<Queued>>();

    private final Thread                          tcpSendThread;
    private final Thread                          tcpRecvThread;
    private final Thread                          multiSendThread;
    private final Thread                          multiRecvThread;

    // Thread safe queues for sending messages
    private final Queue<Data>                     sendTcpQueue;
    private final Queue<Data>                     sendMultiQueue;

    protected final TCP.Peer.RunnableRecv         runnableRecvTcp         = new TCP.Peer.RunnableRecv(listener);
    protected final Multicast.Peer.RunnableRecv   runnableRecvMulti       = new Multicast.Peer.RunnableRecv(listener);
    protected final String                        myName;

    protected Peer(String name) {
        this.myName = name;

        // Receivers

        tcpRecvThread = new Thread(runnableRecvTcp, "recvTcp");
        tcpRecvThread.start();

        multiRecvThread = new Thread(runnableRecvMulti, "recvMulti");
        multiRecvThread.start();

        // Senders

        tcpSendThread = new Thread(runnableSendTcp, "sendTcp");
        sendTcpQueue = runnableSendTcp.getQueue();
        tcpSendThread.start();

        multiSendThread = new Thread(runnableSendMulti, "sendMulti");
        sendMultiQueue = runnableSendMulti.getQueue();
        multiSendThread.start();
    }

    public void shutdown() throws InterruptedException {
        TCP.Peer.RunnableSend.run = false;
        TCP.Peer.RunnableRecv.run = false;

        Multicast.Peer.RunnableSend.run = false;
        Multicast.Peer.RunnableRecv.run = false;

        // Senders

        multiSendThread.interrupt();
        multiSendThread.join();

        tcpSendThread.interrupt();
        tcpSendThread.join();

        // Receivers

        tcpRecvThread.interrupt();
        tcpRecvThread.join();

        multiRecvThread.interrupt();
        multiRecvThread.join();
    }

    public String getName() {
        return myName;
    }

    public boolean isReady() {
        return (runnableRecvTcp.isReady() && runnableSendTcp.isReady() && runnableRecvMulti.isReady() && runnableSendMulti.isReady());
    }

    /** Get encoded public key **/
    protected abstract byte[] getPublicKey();

    private void sendWhois(String who) {
        final byte[] msg = getWhoisMsg(who);
        final Data data = new Data(myName, runnableRecvTcp.getHost(), runnableRecvTcp.getPort(), EVERY_ONE, runnableRecvMulti.getHost(), runnableRecvMulti.getPort(), NO_SIG, msg);
        sendMultiQueue.add(data);
    }

    private void handleWhois(byte[] bytes, Data data) {
        final String name = parseWhoisMsg(bytes);

        // If your name then shout it out!
        if (name.equals(this.myName))
            sendIam();
    }

    private void sendIam() {
        final byte[] msg = getIamMsg(getPublicKey());
        final Data data = new Data(myName, runnableRecvTcp.getHost(), runnableRecvTcp.getPort(), EVERY_ONE, runnableRecvMulti.getHost(), runnableRecvMulti.getPort(), NO_SIG, msg);
        sendMultiQueue.add(data);
    }

    private void handleIam(byte[] bytes, Data data) {
        final String name = data.from;
        final byte[] key = parseIamMsg(bytes);

        // Public key
        newPublicKey(name, key);
    }

    /** What do you want to do with a new public key **/
    protected abstract void newPublicKey(String name, byte[] publicKey);

    /** Sign message with private key **/
    protected abstract byte[] signMsg(byte[] bytes);

    /** Verify the bytes given the public key and signature **/
    protected abstract boolean verifyMsg(byte[] publicKey, byte[] signature, byte[] bytes);

    /** Send coin to the peer named 'to' **/
    protected void sendCoin(String to, Coin coin) {
        final Data d = peers.get(to);
        if (d == null){
            // Could not find peer, broadcast a whois
            addCoinToSend(false, to, coin);
            sendWhois(to);
            return;
        }

        final byte[] msg = getCoinMsg(coin);
        final byte[] sig = signMsg(msg);
        final Data data = new Data(myName, runnableRecvTcp.getHost(), runnableRecvTcp.getPort(), to, d.sourceAddr.getHostAddress(), d.sourcePort, sig, msg);
        sendTcpQueue.add(data);
    }

    private void handleCoin(byte[] bytes, Data data) {
        final Coin coin = parseCoinMsg(bytes);
        final String from = coin.from;
        handleCoin(from, coin, data);
    }

    private void handleCoin(String from, Coin coin, Data data) {
        // Let the app logic do what it needs to
        KeyStatus knownPublicKey = handleCoin(from, coin, data.signature.array(), data.message.array());
        if (knownPublicKey == KeyStatus.NO_PUBLIC_KEY) {
            addCoinToRecv(false, from, coin, data);
            sendWhois(from);
            return;
        } else if (knownPublicKey != KeyStatus.SUCCESS) {
            return;
        }

        // Send an ACK msg
        ackCoin(from, coin);
    }

    /** What do you want to do now that you have received a coin, return false if the public key is unknown **/
    protected abstract KeyStatus handleCoin(String from, Coin coin, byte[] sig, byte[] bytes);

    private void ackCoin(String to, Coin coin) {
        final Data d = peers.get(to);
        if (d == null){
            // Could not find peer, broadcast a whois
            addCoinToSend(true, to, coin);
            sendWhois(to);
            return;
        }

        final byte[] msg = getCoinAck(coin);
        final byte[] sig = signMsg(msg);
        final Data data = new Data(myName, runnableRecvTcp.getHost(), runnableRecvTcp.getPort(), to, d.sourceAddr.getHostAddress(), d.sourcePort, sig, msg);
        sendTcpQueue.add(data);
    }

    private void handleCoinAck(byte[] bytes, Data data) {
        final Coin coin = parseCoinAck(bytes);
        final String to = coin.to; // yes, use the to field.
        handleCoinAck(to, coin, data);
    }

    private void handleCoinAck(String from, Coin coin, Data data) {
        // Let the app logic do what it needs to
        KeyStatus knownPublicKey = handleCoinAck(from, coin, data.signature.array(), data.message.array());
        if (knownPublicKey == KeyStatus.NO_PUBLIC_KEY) {
            addCoinToRecv(true, from, coin, data);
            sendWhois(from);
            return;
        } else if (knownPublicKey != KeyStatus.SUCCESS) {
            return;
        }

        final Transaction trans = getTransaction(coin);
        sendTransaction(trans, data);
    }

    /** What do you want to do now that you received an ACK for a sent coin, return false if the public key is unknown **/
    protected abstract KeyStatus handleCoinAck(String from, Coin coin, byte[] sig, byte[] bytes);

    /** Create a transaction given the this coin **/
    protected abstract Transaction getTransaction(Coin coin);
    
    protected void sendTransaction(Transaction trans, Data d) {
        final byte[] msg = getTransactionMsg(trans);
        final byte[] sig = signMsg(msg);
        final Data data = new Data(myName, runnableRecvTcp.getHost(), runnableRecvTcp.getPort(), d.from, d.sourceAddr.getHostAddress(), d.sourcePort, sig, msg);
        sendTcpQueue.add(data);
    }

    private void handleTransaction(byte[] bytes, Data data) {
        final Transaction trans = parseTransactionMsg(bytes);
        final HashStatus status = checkTransaction(data.from, trans, data.signature.array(), data.message.array());
        if (status != HashStatus.SUCCESS)
            return;

        // Hash looks good to me, ask everyone else
        sendValidation(trans);
    }

    protected void sendValidation(Transaction trans) {
        final byte[] msg = getValidationMsg(trans);
        final byte[] sig = signMsg(msg);
        final Data data = new Data(myName, runnableRecvTcp.getHost(), runnableRecvTcp.getPort(), EVERY_ONE, runnableRecvMulti.getHost(), runnableRecvMulti.getPort(), sig, msg);
        sendMultiQueue.add(data);
    }

    private void handleValidation(byte[] bytes, Data data) {
        final Transaction trans = parseValidationMsg(bytes);
        if (trans.getIsValid()) {
            // Yey! we got a validation from the community
            handleValidation(data.from, trans, data.signature.array(), data.message.array());
            return;
        }

        // Don't validate my own transaction
        final String from = data.from;
        if (from.equals(myName))
            return;

        final HashStatus status = checkTransaction(from, trans, data.signature.array(), data.message.array());
        if (status != HashStatus.SUCCESS)
            return;

        // Hash looks good to me, let everyone know
        trans.setIsValid(true);
        sendValidation(trans);
    }

    /** What do you want to do now that you received an transaction **/
    protected abstract HashStatus checkTransaction(String from, Transaction trans, byte[] signature, byte[] bytes);

    /** What do you want to do now that you received a valid transaction **/
    protected abstract HashStatus handleValidation(String from, Transaction trans, byte[] signature, byte[] bytes);

    private void addCoinToSend(boolean isAck, String to, Coin c) {
        final Queued q = new Queued(isAck, c, null);
        Queue<Queued> l = coinsToSend.get(to);
        if (l == null) {
            l = new ConcurrentLinkedQueue<Queued>();
            coinsToSend.put(to, l);
        }
        l.add(q);
    }

    private void processCoinsToSend(String to) {
        Queue<Queued> l = coinsToSend.get(to);
        if (l==null || l.size()==0)
            return;
        while (l.size()>0) {
            final Queued q = l.poll();
            if (q == null)
                return;
            final Data d = peers.get(to); // Do not use the data object in the queue object
            final byte[] msg = getCoinMsg(q.coin);
            final byte[] sig = signMsg(msg);
            final Data data = new Data(myName, runnableRecvTcp.getHost(), runnableRecvTcp.getPort(), to, d.sourceAddr.getHostAddress(), d.sourcePort, sig, msg);
            sendTcpQueue.add(data);
        }
    }

    private void addCoinToRecv(boolean isAck, String from, Coin c, Data d) {
        final Queued q = new Queued(isAck, c, d);
        Queue<Queued> lc = coinsToRecv.get(from);
        if (lc == null) {
            lc = new ConcurrentLinkedQueue<Queued>();
            coinsToRecv.put(from, lc);
        }
        lc.add(q);
    }

    private void processCoinsToRecv(String from) {
        Queue<Queued> l = coinsToRecv.get(from);
        if (l==null || l.size()==0)
            return;
        while (l.size()>0) {
            final Queued q = l.poll();
            if (q == null)
                return;
            if (q.isAck)
                handleCoinAck(from, q.coin, q.data);
            else
                handleCoin(from, q.coin, q.data);
        }
    }

    private static final class Queued {

        private final boolean   isAck;
        private final Coin      coin;
        private final Data      data;

        private Queued(boolean isAck, Coin coin, Data data) {
            this.isAck = isAck;
            this.coin = coin;
            this.data = data;
        }
    }

    public static final byte[] getIamMsg(byte[] publicKey) {
        final int publicKeyLength = publicKey.length;
        final byte[] msg = new byte[HEADER_LENGTH + KEY_LENGTH + publicKeyLength];
        final ByteBuffer buffer = ByteBuffer.wrap(msg);

        buffer.put(IAM_MSG.getBytes());

        buffer.putInt(publicKeyLength);
        buffer.put(publicKey);

        return msg;
    }

    public static final byte[] parseIamMsg(byte[] bytes) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);

        final byte [] bMsgType = new byte[HEADER_LENGTH];
        buffer.get(bMsgType);

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

    public static final byte[] getTransactionMsg(Transaction trans) {
        final byte[] msg = new byte[HEADER_LENGTH + trans.getBufferLength()];
        final ByteBuffer coinBuffer = ByteBuffer.allocate(trans.getBufferLength());
        trans.toBuffer(coinBuffer);
        final ByteBuffer buffer = ByteBuffer.wrap(msg);
        buffer.put(TRANSACTION.getBytes());

        buffer.put(coinBuffer);

        return msg;
    }

    public static final Transaction parseTransactionMsg(byte[] bytes) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        final byte [] bMsgType = new byte[HEADER_LENGTH];
        buffer.get(bMsgType);

        final Transaction coin = new Transaction();
        coin.fromBuffer(buffer);

        return coin;
    }

    public static final byte[] getValidationMsg(Transaction trans) {
        final byte[] msg = new byte[HEADER_LENGTH + trans.getBufferLength()];
        final ByteBuffer coinBuffer = ByteBuffer.allocate(trans.getBufferLength());
        trans.toBuffer(coinBuffer);
        final ByteBuffer buffer = ByteBuffer.wrap(msg);
        buffer.put(VALIDATION.getBytes());

        buffer.put(coinBuffer);

        return msg;
    }

    public static final Transaction parseValidationMsg(byte[] bytes) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        final byte [] bMsgType = new byte[HEADER_LENGTH];
        buffer.get(bMsgType);

        final Transaction coin = new Transaction();
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
