package com.jwetherell.bitcoin.test;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.data_model.Coin;
import com.jwetherell.bitcoin.data_model.Wallet;

public class WalletTest {

    @Test
    public void testWallet() {
        final int value = 10;
        final Wallet w1 = new Wallet("w1");
        w1.addCoin(new Coin("me","you","ignore",value));
        w1.removeCoin("none",value);

        Assert.assertTrue(w1.getBalance()==0);
    }

    @Test
    public void testWalletBorrowing() {
        final int value = 10;
        final Wallet w1 = new Wallet("w1");
        w1.addCoin(new Coin("me","you","ignore",value));

        Coin b = w1.borrowCoin("none",1);
        Assert.assertTrue(w1.getPending()==1);
        Assert.assertTrue(w1.getBalance()==10);

        w1.returnBorrowedCoin(b);
        Assert.assertTrue(w1.getPending()==0);
        Assert.assertTrue(w1.getBalance()==10);

        b = w1.borrowCoin("none",1);
        Assert.assertTrue(w1.getPending()==1);
        Assert.assertTrue(w1.getBalance()==10);

        w1.removeBorrowedCoin(b);
        Assert.assertTrue(w1.getPending()==0);
        Assert.assertTrue(w1.getBalance()==9);
    }
}
