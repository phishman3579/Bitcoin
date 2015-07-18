package com.jwetherell.bitcoin.test;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.bitcoin.data_model.Block;

public class BlockTest {

    @Test
    public void testSerialization() {
        String f = "me";
        String t = "you";
        String m = "Here is a block for you!";
        int v = 1;

        Block c1 = new Block(f, t, m,v);
        ByteBuffer b = ByteBuffer.allocate(c1.getBufferLength());
        c1.toBuffer(b);

        Block c2 = new Block();
        c2.fromBuffer(b);

        Assert.assertTrue(c1.equals(c2));
    }
}
