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

public class CoinExchangerTest {

    @Test(timeout=1000)
    public void testDupCoin() throws InterruptedException {
        String n1 = "n1";
        String n2 = "n2";
        Coin c1 = new Coin(n1,n2,"Coinage.",10);
        DupCoinExchanger p1 = new DupCoinExchanger(n1);
        DupCoinExchanger p2 = new DupCoinExchanger(n2);

        // Wait for everyone to initialize
        Thread.sleep(250);

        // Send coin
        p1.sendCoin(n2,c1);

        Thread.yield();

        while (p2.getWallet().getBalance()!=10) {
            Thread.yield();
        }
        Assert.assertTrue(p2.getWallet().getBalance()==10);

        // This is a dup and should be dropped
        p1.sendCoin(n2,c1); 

        Thread.yield();

        // This should be accepted
        p1.sendCoin(n2,20);

        Thread.yield();

        while (p2.getWallet().getBalance()!=30) {
            Thread.yield();
        }
        Assert.assertTrue(p2.getWallet().getBalance()==30);

        p1.shutdown();
        p2.shutdown();
    }

    @Test(timeout=1000)
    public void testBadSignature() throws InterruptedException {
        String n1 = "n1";
        String n2 = "n2";
        String n3 = "n3";
        BadKeyCoinExchanger p1 = new BadKeyCoinExchanger(n1);
        p1.getWallet().addCoin(new Coin("me","you","Coinage.",10));
        CoinExchanger p2 = new CoinExchanger(n2);
        p2.getWallet().addCoin(new Coin("me","you","Coinage.",10));
        CoinExchanger p3 = new CoinExchanger(n3);
        p3.getWallet().addCoin(new Coin("me","you","Coinage.",10));

        // Wait for everyone to initialize
        Thread.sleep(250);

        // Send coin (which'll be rejected for a bad signature)
        p1.sendCoin(n2,10);
        // p1=10, p2=10, p3=10

        Thread.yield();

        p2.sendCoin(n3,2);
        // p1=10, p2=8, p3=12

        Thread.yield();

        while (p1.getWallet().getBalance()!=10 || p2.getWallet().getBalance()!=8 || p3.getWallet().getBalance()!=12) {
            Thread.yield();
        }
        Assert.assertTrue(p1.getWallet().getBalance()==10);
        Assert.assertTrue(p2.getWallet().getBalance()==8);
        Assert.assertTrue(p3.getWallet().getBalance()==12);

        p1.shutdown();
        p2.shutdown();
        p3.shutdown();
    }

    @Test(timeout=1000)
    public void testCoinExchangers2() throws InterruptedException {
        String n1 = "n1";
        CoinExchanger p1 = new CoinExchanger(n1);
        p1.getWallet().addCoin(new Coin("me","you","Coinage.",10));

        Thread.yield();

        String n2 = "n2";
        CoinExchanger p2 = new CoinExchanger(n2);
        p2.getWallet().addCoin(new Coin("me","you","Coinage.",20));

        // Wait for everyone to initialize
        Thread.sleep(250);

        p1.sendCoin(n2, 3);
        // p1=7, p2=23
        p2.sendCoin(n1, 7);
        // p1=14, p2=16

        while (p1.getWallet().getBalance()!=14 || p2.getWallet().getBalance()!=16) {
            Thread.yield();
        }

        p1.shutdown();
        p2.shutdown();

        Assert.assertTrue(p1.getWallet().getPending()==0);
        Assert.assertTrue(p2.getWallet().getPending()==0);

        Assert.assertTrue(p1.getWallet().getBalance()==14);
        Assert.assertTrue(p2.getWallet().getBalance()==16);
    }

    @Test(timeout=1000)
    public void testCoinExchangers3() throws InterruptedException {
        String n1 = "n1";
        CoinExchanger p1 = new CoinExchanger(n1);
        p1.getWallet().addCoin(new Coin("me","you","Coinage.",10));

        Thread.yield();

        String n2 = "n2";
        CoinExchanger p2 = new CoinExchanger(n2);
        p2.getWallet().addCoin(new Coin("me","you","Coinage.",20));

        Thread.yield();

        String n3 = "n3";
        CoinExchanger p3 = new CoinExchanger(n3);
        p3.getWallet().addCoin(new Coin("me","you","Coinage.",15));

        // Wait for everyone to initialize
        Thread.sleep(250);

        p1.sendCoin(n2, 3);
        // p1=7, p2=23, p3=15
        p2.sendCoin(n3, 7);
        // p1=7, p2=16, p3=22
        p3.sendCoin(n1, 11);
        // p1=18, p2=16, p3=11

        while (p1.getWallet().getBalance()!=18 || p2.getWallet().getBalance()!=16 || p3.getWallet().getBalance()!=11) {
            Thread.yield();
        }

        p1.shutdown();
        p2.shutdown();
        p3.shutdown();

        Assert.assertTrue(p1.getWallet().getPending()==0);
        Assert.assertTrue(p2.getWallet().getPending()==0);
        Assert.assertTrue(p3.getWallet().getPending()==0);

        Assert.assertTrue(p1.getWallet().getBalance()==18);
        Assert.assertTrue(p2.getWallet().getBalance()==16);
        Assert.assertTrue(p3.getWallet().getBalance()==11);
    }

    private static class DupCoinExchanger extends CoinExchanger {

        public DupCoinExchanger(String name) {
            super(name);
        }

        /** Really only here to open up the method for JUnits **/
        public void sendCoin(String name, Coin coin) {
            super.sendCoin(name,coin);
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
