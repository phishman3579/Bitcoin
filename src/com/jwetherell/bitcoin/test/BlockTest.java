package com.jwetherell.bitcoin.test;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.data_model.Transaction;
import com.jwetherell.bitcoin.data_model.Block;

public class BlockTest {

    @Test
    public void testSerialization() {
        final String f = "me";
        final String t = "you";
        final String m = "Here is a coin for you!";
        final int v = 1;

        final Transaction trans = new Transaction(f, t, m,v);
        byte[] prev = "I am a hash!".getBytes();
        byte[] hash = "I am also a hash!".getBytes();
        final Block block = new Block(f,prev,hash,trans);
        final ByteBuffer b = ByteBuffer.allocate(block.getBufferLength());
        block.toBuffer(b);

        final Block block2 = new Block();
        block2.fromBuffer(b);

        Assert.assertTrue(block.equals(block2));
    }
}
