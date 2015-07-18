package com.jwetherell.bitcoin;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.jwetherell.bitcoin.data_model.BlockChain.HashStatus;
import com.jwetherell.bitcoin.data_model.Block;
import com.jwetherell.bitcoin.data_model.Data;
import com.jwetherell.bitcoin.data_model.ProofOfWork;
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
    private static final String                   WHOIS                   = "Who is      ";
    private static final String                   IAM                     = "I am        ";
    private static final String                   BLOCK                   = "Block       ";
    private static final String                   BLOCK_ACK               = "Block ACK   ";
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
                if (hdr.equals(WHOIS)) {
                    handleWhois(bytes,data);
                } else if (hdr.equals(IAM)) {
                    handleIam(bytes,data);
                    processBlocksToSend(from);
                    processBlocksToRecv(from);
                } else if (hdr.equals(BLOCK)) {
                    handleBlock(bytes,data);
                } else if (hdr.equals(BLOCK_ACK)) {
                    handleBlockAck(bytes,data);
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
    private final Map<String,Queue<Queued>>       blocksToSend             = new ConcurrentHashMap<String,Queue<Queued>>();
    private final Map<String,Queue<Queued>>       blocksToRecv             = new ConcurrentHashMap<String,Queue<Queued>>();

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

    /** Send block to the peer named 'to' **/
    protected void sendCoin(String to, Block block) {
        final Data d = peers.get(to);
        if (d == null){
            // Could not find peer, broadcast a whois
            addBlockToSend(false, to, block);
            sendWhois(to);
            return;
        }

        final byte[] msg = getBlockMsg(block);
        final byte[] sig = signMsg(msg);
        final Data data = new Data(myName, runnableRecvTcp.getHost(), runnableRecvTcp.getPort(), to, d.sourceAddr.getHostAddress(), d.sourcePort, sig, msg);
        sendTcpQueue.add(data);
    }

    private void handleBlock(byte[] bytes, Data data) {
        final Block block = parseBlockMsg(bytes);
        final String from = block.from;
        handleBlock(from, block, data);
    }

    private void handleBlock(String from, Block block, Data data) {
        // Let the app logic do what it needs to
        KeyStatus knownPublicKey = handleBlock(from, block, data.signature.array(), data.message.array());
        if (knownPublicKey == KeyStatus.NO_PUBLIC_KEY) {
            addBlockToRecv(false, from, block, data);
            sendWhois(from);
            return;
        } else if (knownPublicKey != KeyStatus.SUCCESS) {
            return;
        }

        // Send an ACK msg
        ackBlock(from, block);
    }

    /** What do you want to do now that you have received a block, return the KeyStatus **/
    protected abstract KeyStatus handleBlock(String from, Block block, byte[] sig, byte[] bytes);

    private void ackBlock(String to, Block block) {
        final Data d = peers.get(to);
        if (d == null){
            // Could not find peer, broadcast a whois
            addBlockToSend(true, to, block);
            sendWhois(to);
            return;
        }

        final byte[] msg = getBlockAckMsg(block);
        final byte[] sig = signMsg(msg);
        final Data data = new Data(myName, runnableRecvTcp.getHost(), runnableRecvTcp.getPort(), to, d.sourceAddr.getHostAddress(), d.sourcePort, sig, msg);
        sendTcpQueue.add(data);
    }

    private void handleBlockAck(byte[] bytes, Data data) {
        final Block block = parseBlockAckMSg(bytes);
        final String to = block.to; // yes, use the to field.
        handleBlockAck(to, block, data);
    }

    private void handleBlockAck(String from, Block block, Data data) {
        // Let the app logic do what it needs to
        KeyStatus knownPublicKey = handleBlockAck(from, block, data.signature.array(), data.message.array());
        if (knownPublicKey == KeyStatus.NO_PUBLIC_KEY) {
            addBlockToRecv(true, from, block, data);
            sendWhois(from);
            return;
        } else if (knownPublicKey != KeyStatus.SUCCESS) {
            return;
        }

        final Transaction trans = getNextTransaction(block);
        sendTransaction(trans, data);
    }

    /** What do you want to do now that you received an ACK for a sent block, return the KeyStatus **/
    protected abstract KeyStatus handleBlockAck(String from, Block block, byte[] sig, byte[] bytes);

    /** Create a transaction given the this block **/
    protected abstract Transaction getNextTransaction(Block block);
    
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
        if (trans.isValid) {
            // Yey! we got a validation from the community

            // Let's see if the nonce was computed correctly
            boolean nonceComputedCorrectly = ProofOfWork.check(trans.hash, trans.nonce, trans.numberOfZeros);
            if (!nonceComputedCorrectly) {
                System.err.println("Nonce was not computed correctly. trans={\n"+trans.toString()+"\n}");
                return;
            }

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

        // Let's mine this sucker.
        long nonce = mining(trans.hash, trans.numberOfZeros);

        // Hash looks good to me and I have computed a nonce, let everyone know
        trans.isValid = true;
        trans.nonce = nonce;
        sendValidation(trans);
    }

    /** What do you want to do now that you received an transaction, return the HashStatus **/
    protected abstract HashStatus checkTransaction(String from, Transaction trans, byte[] signature, byte[] bytes);

    /** What do you want to do now that you received a valid transaction, return the HashStatus **/
    protected abstract HashStatus handleValidation(String from, Transaction trans, byte[] signature, byte[] bytes);

    /** Mine the nonce sent in the transaction **/
    protected abstract long mining(byte[] sha256, long numberOfZerosInPrefix);

    // synchronized to protected blocksToSend from changing while processing    
    private synchronized void addBlockToSend(boolean isAck, String to, Block c) {
        final Queued q = new Queued(isAck, c, null);
        Queue<Queued> l = blocksToSend.get(to);
        if (l == null) {
            l = new ConcurrentLinkedQueue<Queued>();
            blocksToSend.put(to, l);
        }
        l.add(q);
    }

    // synchronized to protected blocksToSend from changing while processing    
    private synchronized void processBlocksToSend(String to) {
        Queue<Queued> l = blocksToSend.get(to);
        if (l==null || l.size()==0)
            return;
        while (l.size()>0) {
            final Queued q = l.poll();
            if (q == null)
                return;
            final Data d = peers.get(to); // Do not use the data object in the queue object
            final byte[] msg = getBlockMsg(q.block);
            final byte[] sig = signMsg(msg);
            final Data data = new Data(myName, runnableRecvTcp.getHost(), runnableRecvTcp.getPort(), to, d.sourceAddr.getHostAddress(), d.sourcePort, sig, msg);
            sendTcpQueue.add(data);
        }
    }

    // synchronized to protected blocksToRecv from changing while processing    
    private synchronized void addBlockToRecv(boolean isAck, String from, Block c, Data d) {
        final Queued q = new Queued(isAck, c, d);
        Queue<Queued> lc = blocksToRecv.get(from);
        if (lc == null) {
            lc = new ConcurrentLinkedQueue<Queued>();
            blocksToRecv.put(from, lc);
        }
        lc.add(q);
    }

    // synchronized to protected blocksToRecv from changing while processing    
    private synchronized void processBlocksToRecv(String from) {
        Queue<Queued> l = blocksToRecv.get(from);
        if (l==null || l.size()==0)
            return;
        while (l.size()>0) {
            final Queued q = l.poll();
            if (q == null)
                return;
            if (q.isAck)
                handleBlockAck(from, q.block, q.data);
            else
                handleBlock(from, q.block, q.data);
        }
    }

    private static final class Queued {

        private final boolean   isAck;
        private final Block      block;
        private final Data      data;

        private Queued(boolean isAck, Block block, Data data) {
            this.isAck = isAck;
            this.block = block;
            this.data = data;
        }
    }

    public static final byte[] getIamMsg(byte[] publicKey) {
        final int publicKeyLength = publicKey.length;
        final byte[] msg = new byte[HEADER_LENGTH + KEY_LENGTH + publicKeyLength];
        final ByteBuffer buffer = ByteBuffer.wrap(msg);

        buffer.put(IAM.getBytes());

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

        buffer.put(WHOIS.getBytes());
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

    public static final byte[] getBlockMsg(Block block) {
        final byte[] msg = new byte[HEADER_LENGTH + block.getBufferLength()];
        final ByteBuffer blockBuffer = ByteBuffer.allocate(block.getBufferLength());
        block.toBuffer(blockBuffer);
        final ByteBuffer buffer = ByteBuffer.wrap(msg);
        buffer.put(BLOCK.getBytes());

        buffer.put(blockBuffer);

        return msg;
    }

    public static final Block parseBlockMsg(byte[] bytes) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        final byte [] bMsgType = new byte[HEADER_LENGTH];
        buffer.get(bMsgType);

        final Block block = new Block();
        block.fromBuffer(buffer);

        return block;
    }

    public static final byte[] getBlockAckMsg(Block block) {
        final byte[] msg = new byte[HEADER_LENGTH + block.getBufferLength()];
        final ByteBuffer blockBuffer = ByteBuffer.allocate(block.getBufferLength());
        block.toBuffer(blockBuffer);
        final ByteBuffer buffer = ByteBuffer.wrap(msg);
        buffer.put(BLOCK_ACK.getBytes());

        buffer.put(blockBuffer);

        return msg;
    }

    public static final Block parseBlockAckMSg(byte[] bytes) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        final byte [] bMsgType = new byte[HEADER_LENGTH];
        buffer.get(bMsgType);

        final Block block = new Block();
        block.fromBuffer(buffer);

        return block;
    }

    public static final byte[] getTransactionMsg(Transaction trans) {
        final byte[] msg = new byte[HEADER_LENGTH + trans.getBufferLength()];
        final ByteBuffer blockBuffer = ByteBuffer.allocate(trans.getBufferLength());
        trans.toBuffer(blockBuffer);
        final ByteBuffer buffer = ByteBuffer.wrap(msg);
        buffer.put(TRANSACTION.getBytes());

        buffer.put(blockBuffer);

        return msg;
    }

    public static final Transaction parseTransactionMsg(byte[] bytes) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        final byte [] bMsgType = new byte[HEADER_LENGTH];
        buffer.get(bMsgType);

        final Transaction block = new Transaction();
        block.fromBuffer(buffer);

        return block;
    }

    public static final byte[] getValidationMsg(Transaction trans) {
        final byte[] msg = new byte[HEADER_LENGTH + trans.getBufferLength()];
        final ByteBuffer blockBuffer = ByteBuffer.allocate(trans.getBufferLength());
        trans.toBuffer(blockBuffer);
        final ByteBuffer buffer = ByteBuffer.wrap(msg);
        buffer.put(VALIDATION.getBytes());

        buffer.put(blockBuffer);

        return msg;
    }

    public static final Transaction parseValidationMsg(byte[] bytes) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        final byte [] bMsgType = new byte[HEADER_LENGTH];
        buffer.get(bMsgType);

        final Transaction block = new Transaction();
        block.fromBuffer(buffer);

        return block;
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
