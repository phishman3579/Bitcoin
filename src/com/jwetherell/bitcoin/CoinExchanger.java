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
import com.jwetherell.bitcoin.data_model.Coin;
import com.jwetherell.bitcoin.data_model.Transaction;

/**
 * Class which handles the logic of maintaining the wallet including tracking serial numbers and public/private key encryption.
 * 
 * Thread-Safe (Hopefully)
 */
public class CoinExchanger extends Peer {

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

    @Override
    protected byte[] getPublicKey() {
        return bPublicKey;
    }

    @Override
    protected Transaction getTransaction(Coin coin) {
        return blockChain.getNextTransaction(coin);
    }

    @Override
    protected void newPublicKey(String name, byte[] publicKey) {
        // Copy and store the key
        final byte[] copy = new byte[publicKey.length];
        System.arraycopy(publicKey, 0, copy, 0, publicKey.length);
        publicKeys.put(name, ByteBuffer.wrap(copy));
    }

    public void sendCoin(String name, int value) {
        // Borrow the coin from our wallet until we receive an ACK
        final String msg = value+" from "+myName+" to "+name;
        final Coin coin = new Coin(myName, name, msg, value);
        super.sendCoin(name,coin);
    }

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

    @Override
    protected Status handleCoin(String from, Coin coin, byte[] signature, byte[] bytes) {
        if (!publicKeys.containsKey(from))
            return Status.NO_PUBLIC_KEY;

        final byte[] key = publicKeys.get(from).array();
        if (!verifyMsg(key, signature, bytes)) {
            System.err.println("handleCoin() coin NOT verified. coin="+coin.toString());
            return Status.BAD_SIGNATURE;
        }

        return Status.SUCCESS;
    }

    @Override
    protected Status handleCoinAck(String from, Coin coin, byte[] signature, byte[] bytes) {
        if (!publicKeys.containsKey(from))
            return Status.NO_PUBLIC_KEY;

        final byte[] key = publicKeys.get(from).array();
        if (!verifyMsg(key, signature, bytes)) {
            System.err.println("handleCoinAck() coin NOT verified. coin="+coin.toString());
            return Status.BAD_SIGNATURE;
        }

        return Status.SUCCESS;
    }

    @Override
    protected void handleTransaction(Transaction trans) {
        blockChain.addTransaction(trans);
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
