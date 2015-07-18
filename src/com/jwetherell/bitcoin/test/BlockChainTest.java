package com.jwetherell.bitcoin.test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.BlockChain;
import com.jwetherell.bitcoin.data_model.Transaction;

public class BlockChainTest {

    private static final Transaction[] EMPTY = new Transaction[0];

    @Test
    public void test() {

        final byte[] hash1;
        {
            final byte[] hash = "This is a hash".getBytes();
            final Transaction block = new Transaction("me","you","msg",7,EMPTY,EMPTY);
            final ByteBuffer buffer = ByteBuffer.allocate(block.getBufferLength());
            block.toBuffer(buffer);
            buffer.flip();

            final byte[] bytes = buffer.array();
            hash1 = BlockChain.getNextHash(hash, bytes);
        }

        final byte[] hash2;
        {
            byte[] hash = "This is a hash".getBytes();
            final Transaction block = new Transaction("me","you","msg",7,EMPTY,EMPTY);
            final ByteBuffer buffer = ByteBuffer.allocate(block.getBufferLength());
            block.toBuffer(buffer);
            buffer.flip();

            final byte[] bytes = buffer.array();
            hash2 = BlockChain.getNextHash(hash, bytes);
        }

        Assert.assertTrue(Arrays.equals(hash1, hash2));
    }
}
