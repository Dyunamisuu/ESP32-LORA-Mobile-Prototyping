package org.loraos;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Crypto {

    private final SecretKey baseKey;
    private final SecureRandom random = new SecureRandom();

    private final KeyPair ecdhKeyPair;

    private final Map<Integer, byte[]> sharedSecrets = new ConcurrentHashMap<>();

    private final Map<Integer, RatchetState> ratchetStates = new ConcurrentHashMap<>();

    private final Map<Integer, Boolean> keyExchangeComplete = new ConcurrentHashMap<>();

    private final Map<Integer, CountDownLatch> keyExchangeLatches = new ConcurrentHashMap<>();

    private static class RatchetState {
        byte[] sendKey;
        byte[] recvKey;
        int sendCounter;
        int recvCounter;

        RatchetState(byte[] initialKey) {
            this.sendKey = Arrays.copyOf(initialKey, 32);
            this.recvKey = Arrays.copyOf(initialKey, 32);
            this.sendCounter = 0;
            this.recvCounter = 0;
        }

        void ratchetSend() {
            try {
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                sha256.update(sendKey);
                sha256.update(ByteBuffer.allocate(4).putInt(sendCounter++).array());
                sendKey = sha256.digest();
            } catch (Exception e) {
                throw new RuntimeException("Ratchet failed", e);
            }
        }

        void ratchetRecv() {
            try {
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                sha256.update(recvKey);
                sha256.update(ByteBuffer.allocate(4).putInt(recvCounter++).array());
                recvKey = sha256.digest();
            } catch (Exception e) {
                throw new RuntimeException("Ratchet failed", e);
            }
        }
    }

    public Crypto(SecretKey baseKey) throws Exception {
        this.baseKey = baseKey;

        // Generate ECDH key pair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        this.ecdhKeyPair = kpg.generateKeyPair();
    }

    public static SecretKey generateKey() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("ChaCha20");
        kg.init(256);
        return kg.generateKey();
    }

    public byte[] getPublicKey() {
        return ecdhKeyPair.getPublic().getEncoded();
    }

    // Process received public key and derive shared secret
    public void processPublicKey(int peerId, byte[] peerPublicKeyBytes) throws Exception {

        KeyFactory kf = KeyFactory.getInstance("EC");
        X509EncodedKeySpec spec = new X509EncodedKeySpec(peerPublicKeyBytes);
        PublicKey peerPublicKey = kf.generatePublic(spec);

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(ecdhKeyPair.getPrivate());
        ka.doPhase(peerPublicKey, true);

        byte[] sharedSecret = ka.generateSecret();

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        sha256.update(sharedSecret);
        sha256.update(baseKey.getEncoded());
        byte[] derivedKey = sha256.digest();

        sharedSecrets.put(peerId, derivedKey);
        ratchetStates.put(peerId, new RatchetState(derivedKey));
        keyExchangeComplete.put(peerId, true);

        // Signal that key exchange is complete
        CountDownLatch latch = keyExchangeLatches.get(peerId);
        if (latch != null) {
            latch.countDown();
        }
    }

    public boolean hasSharedSecret(int peerId) {
        return keyExchangeComplete.getOrDefault(peerId, false);
    }

    public boolean waitForKeyExchange(int peerId, long timeoutMs) throws InterruptedException {
        if (hasSharedSecret(peerId)) {
            return true;
        }

        CountDownLatch latch = keyExchangeLatches.computeIfAbsent(peerId, k -> new CountDownLatch(1));
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    private RatchetState getRatchetState(int peerId) {
        return ratchetStates.computeIfAbsent(peerId,
                k -> new RatchetState(baseKey.getEncoded()));
    }

    private byte[] generateNonce(int peerId) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update(ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array());
            sha256.update(ByteBuffer.allocate(4).putInt(peerId).array());

            byte[] randomBytes = new byte[8];
            random.nextBytes(randomBytes);
            sha256.update(randomBytes);

            return Arrays.copyOf(sha256.digest(), 12);
        } catch (Exception e) {
            throw new RuntimeException("Nonce generation failed", e);
        }
    }

    private byte[] getEncryptionKey(int peerId, boolean sending) throws Exception {
        RatchetState ratchet = getRatchetState(peerId);
        byte[] baseKeyMaterial = sharedSecrets.getOrDefault(peerId, baseKey.getEncoded());
        byte[] ratchetKey = sending ? ratchet.sendKey : ratchet.recvKey;

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        sha256.update(baseKeyMaterial);
        sha256.update(ratchetKey);

        return sha256.digest();
    }

    public byte[] encrypt(byte[] plaintext, int peerId) throws Exception {
        byte[] nonce = generateNonce(peerId);
        byte[] keyBytes = getEncryptionKey(peerId, true);
        SecretKey key = new SecretKeySpec(keyBytes, 0, 32, "ChaCha20");

        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305/None/NoPadding");
        IvParameterSpec iv = new IvParameterSpec(nonce);
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);

        byte[] cipherTextWithTag = cipher.doFinal(plaintext);
        getRatchetState(peerId).ratchetSend();

        byte[] result = new byte[12 + cipherTextWithTag.length];
        System.arraycopy(nonce, 0, result, 0, 12);
        System.arraycopy(cipherTextWithTag, 0, result, 12, cipherTextWithTag.length);
        return result;
    }

    public byte[] decrypt(byte[] input, int peerId) throws Exception {
        if (input.length < 12 + 16) {
            throw new IllegalArgumentException("ciphertext too short");
        }
        byte[] nonce = Arrays.copyOfRange(input, 0, 12);
        byte[] cipherTextWithTag = Arrays.copyOfRange(input, 12, input.length);

        byte[] keyBytes = getEncryptionKey(peerId, false);
        SecretKey key = new SecretKeySpec(keyBytes, 0, 32, "ChaCha20");

        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305/None/NoPadding");
        IvParameterSpec iv = new IvParameterSpec(nonce);
        cipher.init(Cipher.DECRYPT_MODE, key, iv);

        byte[] decrypted = cipher.doFinal(cipherTextWithTag);
        getRatchetState(peerId).ratchetRecv();

        return decrypted;
    }

    public byte[] encrypt(byte[] plaintext) throws Exception {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305/None/NoPadding");
        IvParameterSpec iv = new IvParameterSpec(nonce);
        cipher.init(Cipher.ENCRYPT_MODE, baseKey, iv);

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
        cipher.init(Cipher.DECRYPT_MODE, baseKey, iv);
        return cipher.doFinal(cipherTextWithTag);
    }

    public String getStats(int peerId) {
        RatchetState ratchet = ratchetStates.get(peerId);
        boolean hasSecret = hasSharedSecret(peerId);

        if (ratchet == null) {
            return "peer " + peerId + " key=none";
        }

        return String.format("peer %d key=%s send=%d recv=%d",
                peerId,
                hasSecret ? "ecdh" : "base",
                ratchet.sendCounter,
                ratchet.recvCounter);
    }
}