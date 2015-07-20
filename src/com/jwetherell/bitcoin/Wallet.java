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
import com.jwetherell.bitcoin.data_model.Transaction;
import com.jwetherell.bitcoin.interfaces.TransactionListener;

/**
 * Class which handles the logic of maintaining the wallet including tracking serial numbers and public/private key encryption.
 * 
 * Thread-Safe (Hopefully)
 */
public class Wallet extends Peer {

    // Number of zeros in prefix of has to compute as the proof of work.
    private static final int                                NUMBER_OF_ZEROS     = 3;
    // Empty list
    private static final Transaction[]                      EMPTY               = new Transaction[0];

    private final KeyPairGenerator                          gen;
    private final SecureRandom                              random;
    private final Signature                                 enc;
    private final Signature                                 dec;
    private final KeyPair                                   pair;
    private final PrivateKey                                privateKey;
    private final KeyFactory                                keyFactory;
    private final PublicKey                                 publicKey;
    private final byte[]                                    bPublicKey;
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
    private final Map<String,ByteBuffer>                    publicKeys      = new ConcurrentHashMap<String,ByteBuffer>();
    // My BLockChain
    private final BlockChain                                blockChain;

    public Wallet(String name) {
        super(name);
        this.blockChain = new BlockChain(name);
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
     */
    @Override
    protected void newPublicKey(String name, byte[] publicKey) {
        publicKeys.put(name, ByteBuffer.wrap(publicKey));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized byte[] signMsg(byte[] bytes) {
        byte[] signed = null;
        try {
            enc.update(bytes);
            signed = enc.sign();
        } catch (Exception e) {
            System.err.println(myName+" Could not encode msg. "+e);
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
            System.err.println(myName+" Could not decode msg. "+e);
        }
        return verified;
    }

    // synchronized to protect the blockchain from chaing while processing
    public synchronized void sendCoin(TransactionListener listener, String name, int value) {
        // protect the blockchain from changing while processing
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
            System.err.println(myName+" Sorry, you do not have enough coins.");
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

    private Constants.Status checkSignature(String from, byte[] signature, byte[] bytes) {
        if (!publicKeys.containsKey(from))
            return Constants.Status.NO_PUBLIC_KEY;

        final byte[] key = publicKeys.get(from).array();
        if (!verifyMsg(key, signature, bytes)) {
            if (DEBUG)
                System.err.println(myName+" Bad signature on key from '"+from+"'");
            return Constants.Status.BAD_SIGNATURE;
        }

        return Constants.Status.SUCCESS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Constants.Status handleTransaction(String from, Transaction transaction, byte[] signature, byte[] bytes) {
        final Constants.Status status = checkSignature(from, signature, bytes);
        if (status != Constants.Status.SUCCESS) {
            if (DEBUG)
                System.err.println(myName+" handleTransaction() status="+status+"\n transaction={\n"+transaction.toString()+"\n}");
            return status;
        }

        return Constants.Status.SUCCESS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Constants.Status handleTransactionAck(String from, Transaction transaction, byte[] signature, byte[] bytes) {
        final Constants.Status status = checkSignature(from, signature, bytes);
        if (status != Constants.Status.SUCCESS) {
            if (DEBUG)
                System.err.println(myName+" handleTransactionAck() status="+status+"\n transaction={\n"+transaction.toString()+"\n}");
            return status;
        }

        return Constants.Status.SUCCESS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Constants.Status checkTransaction(String from, Block block, byte[] signature, byte[] bytes) {
        final Constants.Status status = checkSignature(from, signature, bytes);
        if (status != Constants.Status.SUCCESS) {
            if (DEBUG)
                System.err.println(myName+" checkTransaction() status="+status+"\n"+"block={\n"+block.toString()+"\n}\n");
        }

        if (status == Constants.Status.NO_PUBLIC_KEY)
            return status;
        if (status == Constants.Status.BAD_SIGNATURE)
            return status;
        if (status != Constants.Status.SUCCESS)
            return status;

        return blockChain.checkHash(block);       
    }

    /**
     * {@inheritDoc}
     * 
     * synchronized to protect the blockchain from chaing while processing
     */
    @Override
    protected Constants.Status handleConfirmation(String from, Block block, byte[] signature, byte[] bytes) {
        final Constants.Status status = checkSignature(from, signature, bytes);
        if (status != Constants.Status.SUCCESS) {
            if (DEBUG)
                System.err.println(myName+" checkTransaction() status="+status+"\n"+"block={\n"+block.toString()+"\n}\n");
        }

        if (status == Constants.Status.NO_PUBLIC_KEY)
            return status;
        if (status == Constants.Status.BAD_SIGNATURE)
            return status;
        if (status != Constants.Status.SUCCESS)
            return status;

        Constants.Status blockChainStatus = blockChain.addBlock(block);
        return blockChainStatus;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected long mining(byte[] sha256, long numberOfZerosInPrefix) {
        return ProofOfWork.solve(sha256, numberOfZerosInPrefix);
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
