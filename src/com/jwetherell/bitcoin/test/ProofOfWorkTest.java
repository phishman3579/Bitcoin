package com.jwetherell.bitcoin.test;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.BlockChain;
import com.jwetherell.bitcoin.ProofOfWork;

public class ProofOfWorkTest {

    @Test
    public void test() {
        final int numberOfZerosInPrefix = 4;
        final byte[] sha256 = BlockChain.calculateSha256("Hello world!");
        final long nonce = ProofOfWork.solve(sha256, numberOfZerosInPrefix);

        Assert.assertTrue(ProofOfWork.check(sha256, nonce, numberOfZerosInPrefix));
    }
}
