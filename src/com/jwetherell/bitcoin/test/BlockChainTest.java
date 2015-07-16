package com.jwetherell.bitcoin.test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.data_model.BlockChain;
import com.jwetherell.bitcoin.data_model.Coin;

public class BlockChainTest {

    @Test
    public void test() {

        final byte[] hash1;
        {
            byte[] hash = "This is a hash".getBytes();
            Coin coin = new Coin("me","you","msg",1);
            final ByteBuffer buffer = ByteBuffer.allocate(coin.getBufferLength());
            coin.toBuffer(buffer);
            final byte[] bytes = buffer.array();
            hash1 = BlockChain.getNextHash(hash, bytes);
        }

        final byte[] hash2;
        {
            byte[] hash = "This is a hash".getBytes();
            Coin coin = new Coin("me","you","msg",1);
            final ByteBuffer buffer = ByteBuffer.allocate(coin.getBufferLength());
            coin.toBuffer(buffer);
            final byte[] bytes = buffer.array();
            hash2 = BlockChain.getNextHash(hash, bytes);
        }

        Assert.assertTrue(Arrays.equals(hash1, hash2));
    }
}
