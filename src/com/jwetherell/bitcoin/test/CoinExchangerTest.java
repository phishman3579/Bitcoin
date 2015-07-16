package com.jwetherell.bitcoin.test;

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
        MyCoinExchanger p1 = new MyCoinExchanger(n1);
        MyCoinExchanger p2 = new MyCoinExchanger(n2);

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

    @Test//(timeout=10000)
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

    @Test//(timeout=1000)
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

    private static class MyCoinExchanger extends CoinExchanger {

        public MyCoinExchanger(String name) {
            super(name);
        }

        /** Really only here to open up the method for JUnits **/
        public void sendCoin(String name, Coin coin) {
            super.sendCoin(name,coin);
        }

    }
}
