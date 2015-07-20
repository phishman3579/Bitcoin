package com.jwetherell.bitcoin.common;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

public class KeyUtils {

    public static final byte[] signMsg(Signature enc, byte[] bytes) {
        byte[] signed = null;
        try {
            enc.update(bytes);
            signed = enc.sign();
        } catch (Exception e) {
            System.err.println("Could not encode msg. "+e);
        }
        return signed;
    }

    public static final boolean verifyMsg(KeyFactory keyFactory, Signature dec, byte[] publicKey, byte[] signature, byte[] bytes) {
        boolean verified = false;
        try {
            PublicKey key = keyFactory.generatePublic(new X509EncodedKeySpec(publicKey));
            dec.initVerify(key);
            dec.update(bytes);
            verified = dec.verify(signature);
        } catch (Exception e) {
            System.err.println("Could not decode msg. "+e);
        }
        return verified;
    }
}
