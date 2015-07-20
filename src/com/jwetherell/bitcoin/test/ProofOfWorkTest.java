package com.jwetherell.bitcoin.test;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.ProofOfWork;
import com.jwetherell.bitcoin.common.HashUtils;

public class ProofOfWorkTest {

    @Test
    public void test() {
        final int numberOfZerosInPrefix = 4;
        final byte[] sha256 = HashUtils.calculateSha256("Hello world!");
        final long nonce = ProofOfWork.solve(sha256, numberOfZerosInPrefix);

        Assert.assertTrue(ProofOfWork.check(sha256, nonce, numberOfZerosInPrefix));
    }
}
