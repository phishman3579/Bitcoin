package com.jwetherell.bitcoin;

import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.jwetherell.bitcoin.data_model.Block;
import com.jwetherell.bitcoin.data_model.BlockChain;
import com.jwetherell.bitcoin.data_model.BlockChain.BlockChainStatus;
import com.jwetherell.bitcoin.data_model.ProofOfWork;
import com.jwetherell.bitcoin.data_model.Transaction;

/**
 * Class which handles the logic of maintaining the wallet including tracking serial numbers and public/private key encryption.
 * 
 * Thread-Safe (Hopefully)
 */
public class Wallet extends Peer {

    // Number of zeros in prefix of has to compute as the proof of work.
    private static final int                    NUMBER_OF_ZEROS         = 1;
    // Empty list
    private static final Transaction[]          EMPTY = new Transaction[0];

    private final KeyPairGenerator              gen;
    private final SecureRandom                  random;
    private final Signature                     enc;
    private final Signature                     dec;
    private final KeyPair                       pair;
    private final PrivateKey                    privateKey;
    private final KeyFactory                    keyFactory;
    private final PublicKey                     publicKey;
    private final byte[]                        bPublicKey;
    {
        try {
            gen = KeyPairGenerator.getInstance("DSA", "SUN");
            random = SecureRandom.getInstance("SHA1PRNG", "SUN");
            gen.initialize(512, random);

            enc = Signature.getInstance("SHA1withDSA", "SUN");
            dec = Signature.getInstance("SHA1withDSA", "SUN");

            pair = gen.generateKeyPair();
            privateKey = pair.getPrivate();
            enc.initSign(privateKey);

            publicKey = pair.getPublic();
            bPublicKey = publicKey.getEncoded();

            keyFactory = KeyFactory.getInstance("DSA", "SUN");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Keep track of everyone's name -> public key
    private final Map<String,ByteBuffer>        publicKeys      = new ConcurrentHashMap<String,ByteBuffer>();
    // My BLockChain
    private final BlockChain                    blockChain;

    public Wallet(String name) {
        super(name);
        this.blockChain = new BlockChain();
    }

    public BlockChain getBlockChain() {
        return blockChain;
    }

    public long getBalance() {
        return blockChain.getBalance(myName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected byte[] getPublicKey() {
        return bPublicKey;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Block getNextBlock(Transaction transaction) {
        Block trans = blockChain.getNextBlock(myName, transaction);
        // Need to be confirmed
        trans.confirmed = false;
        // Number of zeros in prefix of hash to compute
        trans.numberOfZeros = NUMBER_OF_ZEROS;
        return trans;
    }

    /**
     * {@inheritDoc}
     * 
     * synchronized to protect publicKeys from changing while processing
     */
    @Override
    protected synchronized void newPublicKey(String name, byte[] publicKey) {
        publicKeys.put(name, ByteBuffer.wrap(publicKey));
    }

    /**
     * {@inheritDoc}
     * 
     * synchronized to protect enc from changing while processing
     */
    @Override
    protected synchronized byte[] signMsg(byte[] bytes) {
        byte[] signed = null;
        try {
            enc.update(bytes);
            signed = enc.sign();
        } catch (Exception e) {
            System.err.println("Could not encode msg. "+e);
        }
        return signed;
    }

    /**
     * {@inheritDoc}
     * 
     * synchronized to protect dec from changing while processing
     */
    @Override
    protected synchronized boolean verifyMsg(byte[] publicKey, byte[] signature, byte[] bytes) {
        boolean verified = false;
        try {
            PublicKey key = keyFactory.generatePublic(new X509EncodedKeySpec(publicKey));
            dec.initVerify(key);
            dec.update(bytes);
            verified = dec.verify(signature);
        } catch (Exception e) {
            System.err.println("Could not decode msg. "+e);
        }
        return verified;
    }

    // synchronized to protected unused from changing during processing
    public synchronized void sendCoin(String name, int value) {
        final List<Transaction> inputList = new ArrayList<Transaction>();
        int coins = 0;
        for (Transaction t : this.blockChain.getUnused()) {
            if (!(t.to.equals(myName))) 
                continue;
            coins += t.value;
            inputList.add(t);
            if (coins >= value)
                break;
        }
        if (coins < value) {
            System.err.println("Sorry, you do not have enough coins.");
            return;
        }
        final Transaction[] inputs = new Transaction[inputList.size()];
        for (int i=0; i<inputList.size(); i++)
            inputs[i] = inputList.get(i);

        List<Transaction> outputList = new ArrayList<Transaction>();
        if (coins > value) {
            // I get some change back
            final int myCoins = coins - value;
            final Transaction t = new Transaction(myName, myName, "I get some change back.", myCoins, inputs, EMPTY);
            outputList.add(t);
        }
        final Transaction t = new Transaction(myName, name, "Here are some coins for you", value, inputs, EMPTY);
        outputList.add(t);

        final Transaction[] outputs = new Transaction[outputList.size()];
        for (int i=0; i<outputList.size(); i++)
            outputs[i] = outputList.get(i);

        final String msg = value+" from "+myName+" to "+name;
        final Transaction transaction = new Transaction(myName, name, msg, value, inputs, outputs);
        super.sendTransaction(name, transaction);
    }

    // synchronized to protect publicKeys from changing while processing
    private synchronized PeerStatus checkSignature(String from, byte[] signature, byte[] bytes) {
        if (!publicKeys.containsKey(from))
            return PeerStatus.NO_PUBLIC_KEY;

        final byte[] key = publicKeys.get(from).array();
        if (!verifyMsg(key, signature, bytes)) {
            System.err.println("Bad signature on key from '"+from+"'");
            return PeerStatus.BAD_SIGNATURE;
        }

        return PeerStatus.SUCCESS;
    }

    /** Mine the nonce sent in the transaction **/
    protected long mining(byte[] sha256, long numberOfZerosInPrefix) {
        return ProofOfWork.solve(sha256, numberOfZerosInPrefix);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PeerStatus handleTransaction(String from, Transaction transaction, byte[] signature, byte[] bytes) {
        final PeerStatus status = checkSignature(from, signature, bytes);
        if (status != PeerStatus.SUCCESS)
            return status;

        return PeerStatus.SUCCESS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PeerStatus handleTransactionAck(String from, Transaction transaction, byte[] signature, byte[] bytes) {
        final PeerStatus status = checkSignature(from, signature, bytes);
        if (status != PeerStatus.SUCCESS)
            return status;

        return PeerStatus.SUCCESS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BlockChainStatus checkTransaction(String from, Block block, byte[] signature, byte[] bytes) {
        final PeerStatus status = checkSignature(from, signature, bytes);
        if (status == PeerStatus.NO_PUBLIC_KEY)
            return BlockChainStatus.NO_PUBLIC_KEY;
        if (status == PeerStatus.BAD_SIGNATURE)
            return BlockChainStatus.BAD_SIGNATURE;

        if (status != PeerStatus.SUCCESS)
            return BlockChainStatus.UNKNOWN;

        return blockChain.checkBlock(block);       
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BlockChainStatus handleConfirmation(String from, Block block, byte[] signature, byte[] bytes) {
        final PeerStatus status = checkSignature(from, signature, bytes);
        if (status != PeerStatus.SUCCESS)
            return BlockChainStatus.BAD_SIGNATURE;

        return blockChain.addBlock(block);       
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(super.toString()).append(" blockChain={").append(blockChain.toString()).append("}");
        return builder.toString();
    }
}
