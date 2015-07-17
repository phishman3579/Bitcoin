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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.jwetherell.bitcoin.data_model.BlockChain;
import com.jwetherell.bitcoin.data_model.BlockChain.HashStatus;
import com.jwetherell.bitcoin.data_model.Coin;
import com.jwetherell.bitcoin.data_model.ProofOfWork;
import com.jwetherell.bitcoin.data_model.Transaction;

/**
 * Class which handles the logic of maintaining the wallet including tracking serial numbers and public/private key encryption.
 * 
 * Thread-Safe (Hopefully)
 */
public class CoinExchanger extends Peer {

    // Number of zeros in prefix of has to compute as the proof of work.
    private static final int                    NUMBER_OF_ZEROS         = 1;

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

    public CoinExchanger(String name) {
        super(name);
        this.blockChain = new BlockChain();
    }

    public BlockChain getBlockChain() {
        return blockChain;
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
    protected Transaction getNextTransaction(Coin coin) {
        Transaction trans = blockChain.getNextTransaction(myName, coin);
        // Need to be validated
        trans.isValid = false;
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

    public void sendCoin(String name, int value) {
        // Borrow the coin from our wallet until we receive an ACK
        final String msg = value+" from "+myName+" to "+name;
        final Coin coin = new Coin(myName, name, msg, value);
        super.sendCoin(name, coin);
    }

    // synchronized to protect publicKeys from changing while processing
    private synchronized KeyStatus checkKey(String from, byte[] signature, byte[] bytes) {
        if (!publicKeys.containsKey(from))
            return KeyStatus.NO_PUBLIC_KEY;

        final byte[] key = publicKeys.get(from).array();
        if (!verifyMsg(key, signature, bytes))
            return KeyStatus.BAD_SIGNATURE;

        return KeyStatus.SUCCESS;
    }

    /** Mine the nonce sent in the transaction **/
    protected long mining(byte[] sha256, long numberOfZerosInPrefix) {
        return ProofOfWork.solve(sha256, numberOfZerosInPrefix);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected KeyStatus handleCoin(String from, Coin coin, byte[] signature, byte[] bytes) {
        final KeyStatus status = checkKey(from, signature, bytes);
        if (status != KeyStatus.SUCCESS) {
            System.err.println("handleCoin() coin NOT verified. coin={\n"+coin.toString()+"\n}");
            return status;
        }

        return KeyStatus.SUCCESS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected KeyStatus handleCoinAck(String from, Coin coin, byte[] signature, byte[] bytes) {
        final KeyStatus status = checkKey(from, signature, bytes);
        if (status != KeyStatus.SUCCESS) {
            System.err.println("handleCoin() coin NOT verified. coin={\n"+coin.toString()+"\n}");
            return status;
        }

        return KeyStatus.SUCCESS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected HashStatus checkTransaction(String from, Transaction trans, byte[] signature, byte[] bytes) {
        final KeyStatus status = checkKey(from, signature, bytes);
        if (status != KeyStatus.SUCCESS) {
            System.err.println("handleCoin() coin NOT verified. trans={\n"+trans.toString()+"\n}");
            return HashStatus.BAD_HASH;
        }

        return blockChain.checkTransaction(trans);       
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected HashStatus handleValidation(String from, Transaction trans, byte[] signature, byte[] bytes) {
        final KeyStatus status = checkKey(from, signature, bytes);
        if (status != KeyStatus.SUCCESS) {
            System.err.println("handleCoin() coin NOT verified. trans={\n"+trans.toString()+"\n}");
            return HashStatus.BAD_HASH;
        }

        return blockChain.addTransaction(trans);       
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
