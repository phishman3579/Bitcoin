package com.jwetherell.bitcoin.test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.jwetherell.bitcoin.BlockChain;
import com.jwetherell.bitcoin.Wallet;
import com.jwetherell.bitcoin.data_model.Block;
import com.jwetherell.bitcoin.data_model.Data;
import com.jwetherell.bitcoin.data_model.Transaction;

public class WalletTest {

    // Create genesis entity
    private Wallet              genesis;

    @Before
    public void startGenesis() {
        genesis = new Wallet(BlockChain.GENESIS_NAME);
    }

    @After
    public void stopGenersis() throws InterruptedException {
        genesis.shutdown();
        genesis = null;
    }

    private static final void distributeGenesisCoins(Wallet genesis, Wallet... wallets) throws InterruptedException {
        long balance = genesis.getBalance();
        final int each = (int) (balance/wallets.length);

        for (Wallet w : wallets) {
            balance -= each;
            // Distribute genesis coins
            genesis.sendCoin(w.getName(), each);
            while (genesis.getBalance()!=balance || w.getBalance()!=each)
                Thread.yield();
        }
    }

    @Test(timeout=10000)
    public void testBadSignature() throws InterruptedException {
        final String n1 = "n1";
        final String n2 = "n2";
        final String n3 = "n3";
        final BadKeyWallet p1 = new BadKeyWallet(n1);
        final Wallet p2 = new Wallet(n2);
        final Wallet p3 = new Wallet(n3);

        // Distribute genesis coins evenly
        distributeGenesisCoins(genesis,p1,p2,p3);
        // genesis=2, p1=16, p2=16, p3=16

        // Switch to use the 'bad' key
        p1.switchKeys();

        // Send coin (which'll be rejected for a bad signature)
        p1.sendCoin(n2,10);
        // p1=16, p2=16, p3=16

        while (p1.getBalance()!=16 || p2.getBalance()!=16 || p3.getBalance()!=16) {
            Thread.yield();
        }

        p2.sendCoin(n3,2);
        // p1=16, p2=14, p3=18

        while (p1.getBalance()!=16 || p2.getBalance()!=14 || p3.getBalance()!=18) {
            Thread.yield();
        }

        Assert.assertTrue(p1.getBalance()==16);
        Assert.assertTrue(p2.getBalance()==14);
        Assert.assertTrue(p3.getBalance()==18);

        p1.shutdown();
        p2.shutdown();
        p3.shutdown();
    }

    @Test(timeout=10000)
    public void testCoinExchangers2() throws InterruptedException {
        final String n1 = "n1";
        final String n2 = "n2";
        final Wallet p1 = new Wallet(n1);
        final Wallet p2 = new Wallet(n2);

        // Distribute genesis coins evenly
        distributeGenesisCoins(genesis,p1,p2);
        // genesis=0, p1=25, p2=25

        p1.sendCoin(n2, 3);
        // genesis=0, p1=22, p2=28

        while (p1.getBalance()!=22 || p2.getBalance()!=28) {
            Thread.yield();
        }

        p2.sendCoin(n1, 7);
        // genesis=0, p1=29, p2=21

        while (p1.getBalance()!=29 || p2.getBalance()!=21) {
            Thread.yield();
        }

        Assert.assertTrue(p1.getBalance()==29);
        Assert.assertTrue(p2.getBalance()==21);

        p1.shutdown();
        p2.shutdown();
    }

    @Test(timeout=10000)
    public void testCoinExchangers3() throws InterruptedException {
        final String n1 = "n1";
        final String n2 = "n2";
        final String n3 = "n3";
        final Wallet p1 = new Wallet(n1);
        final Wallet p2 = new Wallet(n2);
        final Wallet p3 = new Wallet(n3);

        // Distribute genesis coins evenly
        distributeGenesisCoins(genesis,p1,p2,p3);
        // genesis=2, p1=16, p2=16, p3=16

        p1.sendCoin(n2, 3);
        // p1=13, p2=19, p3=16

        while (p1.getBalance()!=13 || p2.getBalance()!=19 || p3.getBalance()!=16) {
            Thread.yield();
        }

        p2.sendCoin(n3, 7);
        // p1=13, p2=12, p3=23

        while (p1.getBalance()!=13 || p2.getBalance()!=12 || p3.getBalance()!=23) {
            Thread.yield();
        }

        p3.sendCoin(n1, 11);
        // p1=24, p2=12, p3=12

        while (p1.getBalance()!=24 || p2.getBalance()!=12 || p3.getBalance()!=12) {
            Thread.yield();
        }

        Assert.assertTrue(p1.getBalance()==24);
        Assert.assertTrue(p2.getBalance()==12);
        Assert.assertTrue(p3.getBalance()==12);

        p1.shutdown();
        p2.shutdown();
        p3.shutdown();
    }

    @Test(timeout=10000)
    public void testBadHash() throws InterruptedException {
        final Transaction[] EMPTY = new Transaction[0];
        final String n1 = "n1";
        final String n2 = "n2";
        final BadHashWallet p1 = new BadHashWallet(n1);
        final BadHashWallet p2 = new BadHashWallet(n2);

        // Distribute genesis coins evenly
        distributeGenesisCoins(genesis,p1,p2);
        // genesis=0, p1=25, p2=25

        // Send coin
        p1.sendCoin(n2,10);
        // p1=15, p2=35

        while (p1.getBalance()!=15 || p2.getBalance()!=35) {
            Thread.yield();
        }

        // This block has a bad hash
        final Transaction block = new Transaction(n1, n2, "Please reject me!", 1, EMPTY, EMPTY);
        final byte[] prev = "This is a bad hash".getBytes();
        final byte[] hash = "This is a VERY bad hash".getBytes();
        final Block trans = new Block(n1, prev, hash, block, 0);
        // Dummy data object, only care about the destination host and port
        final Data data = new Data(p1.getName(), p1.getHost(), p1.getPort(), p2.getName(), p2.getHost(), p2.getPort(), "".getBytes(), "".getBytes());
        p1.sendBlock(trans, data);
        // p1=15, p2=35 (nothing changes)

        while (p1.getBalance()!=15 || p2.getBalance()!=35) {
            Thread.yield();
        }

        // This should be accepted
        p1.sendCoin(n2,10);
        // p1=5, p2=45

        while (p1.getBalance()!=5 || p2.getBalance()!=45) {
            Thread.yield();
        }

        Assert.assertTrue(p1.getBalance()==5);
        Assert.assertTrue(p2.getBalance()==45);

        p1.shutdown();
        p2.shutdown();
    }

    private static class BadHashWallet extends Wallet {

        public BadHashWallet(String name) {
            super(name);
        }

        public String getHost() {
            return runnableRecvTcp.getHost();
        }

        public int getPort() {
            return runnableRecvTcp.getPort();
        }

        /** Really only here to open up the method for JUnits **/
        public void sendBlock(Block block, Data data) {
            super.sendBlock(block, data);
        }
    }

    private static class BadKeyWallet extends Wallet {

        private final KeyPairGenerator              gen;
        private final SecureRandom                  random;
        private final Signature                     enc;
        private final KeyPair                       pair;
        private final PrivateKey                    privateKey;
        {
            try {
                gen = KeyPairGenerator.getInstance("DSA", "SUN");
                random = SecureRandom.getInstance("SHA1PRNG", "SUN");
                gen.initialize(512, random);

                enc = Signature.getInstance("SHA1withDSA", "SUN");

                pair = gen.generateKeyPair();
                privateKey = pair.getPrivate();
                enc.initSign(privateKey);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private boolean                             switchToBadKey = false;;

        public BadKeyWallet(String name) {
            super(name);
        }

        public void switchKeys() {
            switchToBadKey = !switchToBadKey;
        }

        @Override
        protected byte[] signMsg(byte[] bytes) {
            if (!switchToBadKey)
                return super.signMsg(bytes);

            byte[] signed = null;
            try {
                enc.update(bytes);
                signed = enc.sign();
            } catch (Exception e) {
                System.err.println("Could not encode msg. "+e);
            }
            return signed;
        }
    }
}
