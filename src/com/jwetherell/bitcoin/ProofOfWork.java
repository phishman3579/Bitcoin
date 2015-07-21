package com.jwetherell.bitcoin;

import java.nio.ByteBuffer;

import com.jwetherell.bitcoin.common.HashUtils;

public class ProofOfWork {

    private static final byte getBit(int ID, int position) {
       return (byte) ((ID >> position) & (byte)1);
    }

    /** 
     * Given the sha256 hash, what number can we append to the hash which'll create a hash
     * which has a leading 'numberOfZerosInPrefix' number of zeros.
     * 
     * e.g.
     * if numberOfZerosInPrefix == 3
     * input = 7982970534e089b839957b7e174725ce1878731ed6d700766e59cb16f1c25e27
     * solution = 0000fedb24e31adb71559d43a9a3ddc8be7606fa6befe3260b3eee2cf2aeb642
     * output = 21080
     * 
     **/
    public static final int solve(byte[] sha256, long numberOfZerosInPrefix) {
        final int length = sha256.length;

        final ByteBuffer buffer = ByteBuffer.allocate(length+4);
        buffer.put(sha256, 0, length);

        int x = 0;
        while (x < Integer.MAX_VALUE) {
            // append x
            buffer.putInt(length, x);
            // calculate new hash
            final byte[] result = HashUtils.calculateSha256(buffer.array());
            // wrap in buffer for easier processing
            final ByteBuffer bb = ByteBuffer.wrap(result);

            boolean wrong = false;
            boolean done = false;
            int numOfZeros = 0;
            for (int i=0; i<bb.limit(); i++) {
                final byte b = bb.get(i);
                for (int j=0; j<8; j++) {
                    final byte a = getBit(b,(i*8)+j);
                    if (a == 0) {
                        numOfZeros++;
                    } else {
                        wrong = true;
                        break;
                    }
                    if (numOfZeros == numberOfZerosInPrefix) {
                        done = true;
                        break;
                    }
                }
                if (done || wrong)
                    break;
            }
            if (done)
                break;
            x++;
        }
        return x;
    }

    /** Does the given nonce create a hash which starts with 'numberOfZerosInPrefix' number of zeros **/
    public static final boolean check(byte[] sha256, int nonce, long numberOfZerosInPrefix) {
        final int length = sha256.length;

        final ByteBuffer buffer = ByteBuffer.allocate(length+4);
        buffer.put(sha256, 0, length);

        // append nonce
        buffer.putInt(length, nonce);
        // calculate new hash
        final byte[] result = HashUtils.calculateSha256(buffer.array());
        // wrap in buffer for easier processing
        final ByteBuffer bb = ByteBuffer.wrap(result);

        boolean incorrect = false;
        boolean correct = false;
        int numOfZeros = 0;
        for (int i=0; i<bb.limit(); i++) {
            final byte b = bb.get(i);
            for (int j=0; j<8; j++) {
                final byte a = getBit(b,(i*8)+j);
                if (a == 0) {
                    numOfZeros++;
                } else {
                    incorrect = true;
                    break;
                }
                if (numOfZeros == numberOfZerosInPrefix) {
                    correct = true;
                    break;
                }
            }
            if (correct || incorrect)
                break;
        }
        return correct;
    }
}
