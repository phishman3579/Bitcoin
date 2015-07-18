package com.jwetherell.bitcoin.test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.Wallet;
import com.jwetherell.bitcoin.data_model.Transaction;
import com.jwetherell.bitcoin.data_model.Data;
import com.jwetherell.bitcoin.data_model.Block;

public class WalletTest {

    @Test(timeout=5000)
    public void testBadSignature() throws InterruptedException {
        String n1 = "n1";
        String n2 = "n2";
        String n3 = "n3";
        BadKeyWallet p1 = new BadKeyWallet(n1);
        Wallet p2 = new Wallet(n2);
        Wallet p3 = new Wallet(n3);

        // Wait for everyone to initialize
        while (p1.isReady()==false || p2.isReady()==false || p3.isReady()==false) {
            Thread.yield();
        }

        // Send coin (which'll be rejected for a bad signature)
        p1.sendCoin(n2,10);
        // p1=0, p2=0, p3=0

        while (p1.getBalance()!=0 || p2.getBalance()!=0 || p3.getBalance()!=0) {
            Thread.yield();
        }

        p2.sendCoin(n3,2);
        // p1=0, p2=-2, p3=2

        while (p1.getBalance()!=0 || p2.getBalance()!=-2 || p3.getBalance()!=2) {
            Thread.yield();
        }

        p1.shutdown();
        p2.shutdown();
        p3.shutdown();

        Assert.assertTrue(p1.getBlockChain().equals(p2.getBlockChain()));

        Assert.assertTrue(p1.getBalance()==0);
        Assert.assertTrue(p2.getBalance()==-2);
        Assert.assertTrue(p3.getBalance()==2);
    }

    @Test(timeout=5000)
    public void testCoinExchangers2() throws InterruptedException {
        String n1 = "n1";
        String n2 = "n2";
        Wallet p1 = new Wallet(n1);
        Wallet p2 = new Wallet(n2);

        // Wait for everyone to initialize
        while (p1.isReady()==false || p2.isReady()==false) {
            Thread.yield();
        }

        p1.sendCoin(n2, 3);
        // p1=-3, p2=3

        while (p1.getBalance()!=-3 || p2.getBalance()!=3) {
            Thread.yield();
        }

        p2.sendCoin(n1, 7);
        // p1=4, p2=-7

        while (p1.getBalance()!=4 || p2.getBalance()!=-4) {
            Thread.yield();
        }

        p1.shutdown();
        p2.shutdown();

        Assert.assertTrue(p1.getBlockChain().equals(p2.getBlockChain()));

        Assert.assertTrue(p1.getBalance()==4);
        Assert.assertTrue(p2.getBalance()==-4);
    }

    @Test(timeout=5000)
    public void testCoinExchangers3() throws InterruptedException {
        String n1 = "n1";
        String n2 = "n2";
        String n3 = "n3";
        Wallet p1 = new Wallet(n1);
        Wallet p2 = new Wallet(n2);
        Wallet p3 = new Wallet(n3);

        // Wait for everyone to initialize
        while (p1.isReady()==false || p2.isReady()==false || p3.isReady()==false) {
            Thread.yield();
        }

        p1.sendCoin(n2, 3);
        // p1=-3, p2=3, p3=0

        while (p1.getBalance()!=-3 || p2.getBalance()!=3 || p3.getBalance()!=0) {
            Thread.yield();
        }

        p2.sendCoin(n3, 7);
        // p1=-3, p2=-4, p3=7

        while (p1.getBalance()!=-3 || p2.getBalance()!=-4 || p3.getBalance()!=7) {
            Thread.yield();
        }

        p3.sendCoin(n1, 11);
        // p1=8, p2=-4, p3=-4

        while (p1.getBalance()!=8 || p2.getBalance()!=-4 || p3.getBalance()!=-4) {
            Thread.yield();
        }

        p1.shutdown();
        p2.shutdown();
        p3.shutdown();

        Assert.assertTrue(p1.getBlockChain().equals(p2.getBlockChain()));
        Assert.assertTrue(p2.getBlockChain().equals(p3.getBlockChain()));

        Assert.assertTrue(p1.getBalance()==8);
        Assert.assertTrue(p2.getBalance()==-4);
        Assert.assertTrue(p3.getBalance()==-4);
    }

    @Test(timeout=5000)
    public void testBadHash() throws InterruptedException {
        String n1 = "n1";
        String n2 = "n2";
        DuplicateWallet p1 = new DuplicateWallet(n1);
        DuplicateWallet p2 = new DuplicateWallet(n2);

        // Wait for everyone to initialize
        while (p1.isReady()==false || p2.isReady()==false) {
            Thread.yield();
        }

        // Send coin
        p1.sendCoin(n2,10);

        while (p1.getBalance()!=-10 && p2.getBalance()!=10) {
            Thread.yield();
        }

        // This has a bad hash
        Transaction block = new Transaction(n1, n2, "Please reject me!", 10);
        byte[] prev = "This is a bad hash".getBytes();
        byte[] hash = "This is a VERY bad hash".getBytes();
        Block trans = new Block(n1, prev, hash, block);
        // Dummy data object, only care about the dest host and port
        Data data = new Data(p1.getName(), p1.getHost(), p1.getPort(), p2.getName(), p2.getHost(), p2.getPort(), "".getBytes(), "".getBytes());
        p1.sendBlock(trans, data);

        while (p1.getBalance()!=-10 && p2.getBalance()!=10) {
            Thread.yield();
        }

        // This should be accepted
        p1.sendCoin(n2,20);

        while (p2.getBalance()!=30) {
            Thread.yield();
        }

        p1.shutdown();
        p2.shutdown();

        Assert.assertTrue(p1.getBlockChain().equals(p2.getBlockChain()));

        Assert.assertTrue(p2.getBalance()==30);
    }

    private static class DuplicateWallet extends Wallet {

        public DuplicateWallet(String name) {
            super(name);
        }

        public String getHost() {
            return runnableRecvTcp.getHost();
        }

        public int getPort() {
            return runnableRecvTcp.getPort();
        }

        /** Really only here to open up the method for JUnits **/
        public void sendBlock(Block trans, Data data) {
            super.sendBlock(trans, data);
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

        public BadKeyWallet(String name) {
            super(name);
        }

        @Override
        protected byte[] signMsg(byte[] bytes) {
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
