package com.jwetherell.bitcoin.test;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.ProofOfWork;
import com.jwetherell.bitcoin.common.HashUtils;

public class ProofOfWorkTest {

    @Test
    public void test() {
        final int numberOfZerosInPrefix = 64;
        final byte[] sha256 = HashUtils.calculateSha256("Hello world!");
        final int nonce = ProofOfWork.solve(sha256, numberOfZerosInPrefix);

        Assert.assertTrue(ProofOfWork.check(sha256, nonce, numberOfZerosInPrefix));
    }

    @Test
    public void test2() {
        final int numberOfZerosInPrefix = 64;
        final byte[] sha256 = HashUtils.calculateSha256("Hello, I am a very nice hash. I work well with others and whatnot.");
        final int nonce = ProofOfWork.solve(sha256, numberOfZerosInPrefix);

        Assert.assertTrue(ProofOfWork.check(sha256, nonce, numberOfZerosInPrefix));
    }
}
