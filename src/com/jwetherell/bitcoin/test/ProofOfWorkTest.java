package com.jwetherell.bitcoin.test;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.data_model.BlockChain;
import com.jwetherell.bitcoin.data_model.ProofOfWork;

public class ProofOfWorkTest {

    @Test
    public void test() {
        final int numberOfZerosInPrefix = 2;
        final byte[] sha256 = BlockChain.calculateSha256("Hello world!");
        final long nonce = ProofOfWork.solve(sha256, numberOfZerosInPrefix);

        Assert.assertTrue(nonce==114);
    }
}
