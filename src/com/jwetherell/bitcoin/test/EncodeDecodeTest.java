package com.jwetherell.bitcoin.test;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

import org.junit.Assert;
import org.junit.Test;

public class EncodeDecodeTest {
    
    @Test
    public void test1() {
        byte[] data = "hello.".getBytes();

        /* Test generating and verifying a DSA signature */
        try {
            /* generate a key pair */
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA");
            keyGen.initialize(1024, new SecureRandom());
            KeyPair pair = keyGen.generateKeyPair();

            /* create a Signature object to use
             * for signing and verifying */
            Signature dsa = Signature.getInstance("SHA/DSA"); 

            /* initialize the Signature object for signing */
            PrivateKey priv = pair.getPrivate();
            dsa.initSign(priv);

            /* Update and sign the data */
            dsa.update(data);

            /* Now that all the data to be signed
             * has been read in, sign it */
            byte[] sig = dsa.sign();

            /* Verify the signature */

            /* Initialize the Signature object for verification */
            PublicKey pub = pair.getPublic();
            dsa.initVerify(pub);

            /* Update and verify the data */
            dsa.update(data);

            boolean verifies = dsa.verify(sig);
            Assert.assertTrue(verifies);
        } catch (Exception e) {
            System.err.println("Caught exception " + e.toString());
        }
    }
    
    @Test
    public void test2() {
        byte[] data = "hello.".getBytes();

        /* Test generating and verifying a DSA signature */
        try {
            /* generate a key pair */
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA");
            keyGen.initialize(1024, new SecureRandom());
            KeyPair pair = keyGen.generateKeyPair();

            /* create a Signature object to use
             * for signing and verifying */
            Signature dsa = Signature.getInstance("SHA/DSA"); 

            /* initialize the Signature object for signing */
            PrivateKey priv = pair.getPrivate();
            dsa.initSign(priv);

            /* Update and sign the data */
            dsa.update(data);

            /* Now that all the data to be signed
             * has been read in, sign it */
            byte[] sig = dsa.sign();

            /* Verify the signature */

            /* Initialize the Signature object for verification */
            PublicKey pub = pair.getPublic();
            /* Encode the public key into a byte array */
            byte[] encoded = pub.getEncoded();
            /* Get the public key from the encoded byte array */
            PublicKey fromEncoded = KeyFactory.getInstance("DSA", "SUN").generatePublic(new X509EncodedKeySpec(encoded));
            dsa.initVerify(fromEncoded);

            /* Update and verify the data */
            dsa.update(data);

            boolean verifies = dsa.verify(sig);
            Assert.assertTrue(verifies);
        } catch (Exception e) {
            System.err.println("Caught exception " + e.toString());
        }
    }
}
