package com.jwetherell.bitcoin.test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.CoinExchanger;
import com.jwetherell.bitcoin.data_model.Coin;
import com.jwetherell.bitcoin.data_model.Data;
import com.jwetherell.bitcoin.data_model.Transaction;

public class CoinExchangerTest {

    @Test(timeout=5000)
    public void testBadSignature() throws InterruptedException {
        String n1 = "n1";
        String n2 = "n2";
        String n3 = "n3";
        BadKeyCoinExchanger p1 = new BadKeyCoinExchanger(n1);
        CoinExchanger p2 = new CoinExchanger(n2);
        CoinExchanger p3 = new CoinExchanger(n3);

        // Wait for everyone to initialize
        while (p1.isReady()==false || p2.isReady()==false || p3.isReady()==false) {
            Thread.yield();
        }

        // Send coin (which'll be rejected for a bad signature)
        p1.sendCoin(n2,10);
        // p1=0, p2=0, p3=0

        while (p1.getBlockChain().getBalance(p1.getName())!=0 || p2.getBlockChain().getBalance(p2.getName())!=0 || p3.getBlockChain().getBalance(p3.getName())!=0) {
            Thread.yield();
        }

        p2.sendCoin(n3,2);
        // p1=0, p2=-2, p3=2

        while (p1.getBlockChain().getBalance(p1.getName())!=0 || p2.getBlockChain().getBalance(p2.getName())!=-2 || p3.getBlockChain().getBalance(p3.getName())!=2) {
            Thread.yield();
        }

        p1.shutdown();
        p2.shutdown();
        p3.shutdown();

        Assert.assertTrue(p1.getBlockChain().equals(p2.getBlockChain()));

        Assert.assertTrue(p1.getBlockChain().getBalance(p1.getName())==0);
        Assert.assertTrue(p2.getBlockChain().getBalance(p2.getName())==-2);
        Assert.assertTrue(p3.getBlockChain().getBalance(p3.getName())==2);
    }

    @Test(timeout=5000)
    public void testCoinExchangers2() throws InterruptedException {
        String n1 = "n1";
        String n2 = "n2";
        CoinExchanger p1 = new CoinExchanger(n1);
        CoinExchanger p2 = new CoinExchanger(n2);

        // Wait for everyone to initialize
        while (p1.isReady()==false || p2.isReady()==false) {
            Thread.yield();
        }

        p1.sendCoin(n2, 3);
        // p1=-3, p2=3

        while (p1.getBlockChain().getBalance(p1.getName())!=-3 || p2.getBlockChain().getBalance(p2.getName())!=3) {
            Thread.yield();
        }

        p2.sendCoin(n1, 7);
        // p1=4, p2=-7

        while (p1.getBlockChain().getBalance(p1.getName())!=4 || p2.getBlockChain().getBalance(p2.getName())!=-4) {
            Thread.yield();
        }

        p1.shutdown();
        p2.shutdown();

        Assert.assertTrue(p1.getBlockChain().equals(p2.getBlockChain()));

        Assert.assertTrue(p1.getBlockChain().getBalance(p1.getName())==4);
        Assert.assertTrue(p2.getBlockChain().getBalance(p2.getName())==-4);
    }

    @Test(timeout=5000)
    public void testCoinExchangers3() throws InterruptedException {
        String n1 = "n1";
        String n2 = "n2";
        String n3 = "n3";
        CoinExchanger p1 = new CoinExchanger(n1);
        CoinExchanger p2 = new CoinExchanger(n2);
        CoinExchanger p3 = new CoinExchanger(n3);

        // Wait for everyone to initialize
        while (p1.isReady()==false || p2.isReady()==false || p3.isReady()==false) {
            Thread.yield();
        }

        p1.sendCoin(n2, 3);
        // p1=-3, p2=3, p3=0

        while (p1.getBlockChain().getBalance(p1.getName())!=-3 || p2.getBlockChain().getBalance(p2.getName())!=3 || p3.getBlockChain().getBalance(p3.getName())!=0) {
            Thread.yield();
        }

        p2.sendCoin(n3, 7);
        // p1=-3, p2=-4, p3=7

        while (p1.getBlockChain().getBalance(p1.getName())!=-3 || p2.getBlockChain().getBalance(p2.getName())!=-4 || p3.getBlockChain().getBalance(p3.getName())!=7) {
            Thread.yield();
        }

        p3.sendCoin(n1, 11);
        // p1=8, p2=-4, p3=-4

        while (p1.getBlockChain().getBalance(p1.getName())!=8 || p2.getBlockChain().getBalance(p2.getName())!=-4 || p3.getBlockChain().getBalance(p3.getName())!=-4) {
            Thread.yield();
        }

        p1.shutdown();
        p2.shutdown();
        p3.shutdown();

        Assert.assertTrue(p1.getBlockChain().equals(p2.getBlockChain()));
        Assert.assertTrue(p2.getBlockChain().equals(p3.getBlockChain()));

        Assert.assertTrue(p1.getBlockChain().getBalance(p1.getName())==8);
        Assert.assertTrue(p2.getBlockChain().getBalance(p2.getName())==-4);
        Assert.assertTrue(p3.getBlockChain().getBalance(p3.getName())==-4);
    }

    @Test(timeout=5000)
    public void testBadHash() throws InterruptedException {
        String n1 = "n1";
        String n2 = "n2";
        DupCoinExchanger p1 = new DupCoinExchanger(n1);
        DupCoinExchanger p2 = new DupCoinExchanger(n2);

        // Wait for everyone to initialize
        while (p1.isReady()==false || p2.isReady()==false) {
            Thread.yield();
        }

        // Send coin
        p1.sendCoin(n2,10);

        while (p1.getBlockChain().getBalance(p1.getName())!=-10 && p2.getBlockChain().getBalance(p2.getName())!=10) {
            Thread.yield();
        }

        // This has a bad hash
        Coin coin = new Coin(n1, n2, "Please reject me!", 10);
        byte[] hash = "This is a VERY bad hash".getBytes();
        Transaction trans = new Transaction(n1, hash, coin);
        // Dummy data object, only care about the dest host and port
        Data data = new Data(p1.getName(), p1.getHost(), p1.getPort(), p2.getName(), p2.getHost(), p2.getPort(), "".getBytes(), "".getBytes());
        p1.sendTransaction(trans, data);

        while (p1.getBlockChain().getBalance(p1.getName())!=-10 && p2.getBlockChain().getBalance(p2.getName())!=10) {
            Thread.yield();
        }

        // This should be accepted
        p1.sendCoin(n2,20);

        while (p2.getBlockChain().getBalance(p2.getName())!=30) {
            Thread.yield();
        }

        p1.shutdown();
        p2.shutdown();

        Assert.assertTrue(p1.getBlockChain().equals(p2.getBlockChain()));

        Assert.assertTrue(p2.getBlockChain().getBalance(p2.getName())==30);
    }

    private static class DupCoinExchanger extends CoinExchanger {

        public DupCoinExchanger(String name) {
            super(name);
        }

        public String getHost() {
            return runnableRecvTcp.getHost();
        }

        public int getPort() {
            return runnableRecvTcp.getPort();
        }

        /** Really only here to open up the method for JUnits **/
        public void sendTransaction(Transaction trans, Data data) {
            super.sendTransaction(trans, data);
        }
    }

    private static class BadKeyCoinExchanger extends CoinExchanger {

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

        public BadKeyCoinExchanger(String name) {
            super(name);
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
    }
}
