package com.jwetherell.bitcoin.test;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.Peer;
import com.jwetherell.bitcoin.ProofOfWork;
import com.jwetherell.bitcoin.common.HashUtils;

public class ProofOfWorkTest {

    private Peer.MiningTask task = new Peer.MiningTask();

    @Test
    public void test() {
        final int numberOfZerosInPrefix = 32;
        final byte[] sha256 = HashUtils.calculateSha256("Hello world!");
        task.run = true;
        final int nonce = ProofOfWork.solve(task, sha256, numberOfZerosInPrefix);

        Assert.assertTrue(ProofOfWork.check(sha256, nonce, numberOfZerosInPrefix));
    }

    @Test
    public void test2() {
        final int numberOfZerosInPrefix = 32;
        final byte[] sha256 = HashUtils.calculateSha256("Hello, I am a very nice hash. I work well with others and whatnot.");
        task.run = true;
        final int nonce = ProofOfWork.solve(task, sha256, numberOfZerosInPrefix);

        Assert.assertTrue(ProofOfWork.check(sha256, nonce, numberOfZerosInPrefix));
    }
}
