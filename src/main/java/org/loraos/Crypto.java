package org.loraos;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.security.SecureRandom;
import java.util.Arrays;

public class Crypto {

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public Crypto(SecretKey key) {
        this.key = key;
    }

    // Eenmalig gebruiken om een key te genereren, dan Base64 opslaan
    public static SecretKey generateKey() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("ChaCha20");
        kg.init(256);
        return kg.generateKey();
    }

    // Encrypt: [12 bytes nonce][cipher+tag]
    public byte[] encrypt(byte[] plaintext) throws Exception {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305/None/NoPadding");
        IvParameterSpec iv = new IvParameterSpec(nonce);
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);

        byte[] cipherTextWithTag = cipher.doFinal(plaintext);

        byte[] result = new byte[12 + cipherTextWithTag.length];
        System.arraycopy(nonce, 0, result, 0, 12);
        System.arraycopy(cipherTextWithTag, 0, result, 12, cipherTextWithTag.length);
        return result;
    }

    public byte[] decrypt(byte[] input) throws Exception {
        if (input.length < 12 + 16) {
            throw new IllegalArgumentException("ciphertext too short");
        }
        byte[] nonce = Arrays.copyOfRange(input, 0, 12);
        byte[] cipherTextWithTag = Arrays.copyOfRange(input, 12, input.length);

        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305/None/NoPadding");
        IvParameterSpec iv = new IvParameterSpec(nonce);
        cipher.init(Cipher.DECRYPT_MODE, key, iv);
        return cipher.doFinal(cipherTextWithTag);
    }
}
