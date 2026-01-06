package org.loraos;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

public class MeshNode implements Runnable {
    private final byte id;
    private final LoRaInterface radio;
    private volatile boolean running = true;
    private int seq = 0;

    private final Crypto crypto;
    private final Set<String> seenPackets = new HashSet<>();

    // Track if we initiated key exchange (to avoid response loops)
    private final Set<Integer> keyExchangeInitiated = new HashSet<>();

    private static final String KEY_B64 =
            "8m7FZ5i7g2zQqZ4X7qX4yY0o5S8+H3y0uA7c3bTtq2Q=";

    public MeshNode(byte id, LoRaInterface radio) throws Exception {
        this.id = id;
        this.radio = radio;

        byte[] keyBytes = Base64.getDecoder().decode(KEY_B64);
        SecretKey key = new SecretKeySpec(keyBytes, "ChaCha20");
        this.crypto = new Crypto(key);
    }

    public void sendChat(byte dst, String text, boolean privateDirect) throws Exception {
        int dstId = dst & 0xFF;

        // Ensure key exchange is complete before sending encrypted data
        if (!crypto.hasSharedSecret(dstId)) {
            synchronized (keyExchangeInitiated) {
                if (!keyExchangeInitiated.contains(dstId)) {
                    log("starting key exchange with " + dstId);
                    keyExchangeInitiated.add(dstId);
                    sendKeyExchange(dst);
                }
            }

            // Wait for key exchange to complete (max 2 seconds)
            if (!crypto.waitForKeyExchange(dstId, 2000)) {
                log("key exchange timeout with " + dstId);
                return;
            }
        }

        byte baseType = MessageType.CHAT;
        byte finalType = privateDirect
                ? MessageType.withNoForward(baseType)
                : baseType;

        sendTo(dst, text, finalType, (byte) 5);
    }

    private void sendKeyExchange(byte dst) throws Exception {
        byte[] publicKey = crypto.getPublicKey();

        if (publicKey.length > 255) {
            throw new IllegalArgumentException("Public key too large");
        }

        Packet p = new Packet(id, dst, MessageType.KEY_EXCHANGE, (byte) 5, nextSeq(), publicKey);
        radio.send(p);
        log("public key sent to " + (dst & 0xFF));
    }

    public void stop() { running = false; }

    private void log(String msg) {
        System.out.println("[Node " + (id & 0xFF) + "] " + msg);
    }

    public void sendTo(byte dst, String text, byte type, byte ttl) throws Exception {
        byte[] plainPayload = text.getBytes();
        int dstId = dst & 0xFF;
        byte[] encrypted = crypto.encrypt(plainPayload, dstId);

        if (encrypted.length > 255) {
            throw new IllegalArgumentException("Encrypted payload too large for Packet");
        }

        Packet p = new Packet(id, dst, type, ttl, nextSeq(), encrypted);
        radio.send(p);
        log("sent to " + dstId + " [" + crypto.getStats(dstId) + "]");
    }

    private synchronized int nextSeq() { return seq++; }

    private boolean isDuplicate(Packet p) {
        String key = (p.srcId & 0xFF) + ":" + p.seq;
        if (seenPackets.contains(key)) {
            return true;
        }
        seenPackets.add(key);
        return false;
    }

    private void sendAck(byte dst, int seqToAck) throws Exception {
        String ackMsg = "ACK " + seqToAck;
        byte[] plain = ackMsg.getBytes();
        int dstId = dst & 0xFF;
        byte[] encrypted = crypto.encrypt(plain, dstId);
        if (encrypted.length > 255) return;

        Packet ack = new Packet(id, dst, MessageType.ACK, (byte) 3, seqToAck, encrypted);
        radio.send(ack);
    }

    private void handleCommand(String msg) {
        if (msg.equals("PING")) {
            log("got PING");
        } else {
            log("unknown CMD: " + msg);
        }
    }

    private void handle(Packet p) throws Exception {
        if (isDuplicate(p)) {
            return;
        }

        if (p.dstId == id) {
            int srcId = p.srcId & 0xFF;
            byte baseType = MessageType.baseType(p.type);

            if (baseType == MessageType.KEY_EXCHANGE) {
                log(" received public key from " + srcId);
                crypto.processPublicKey(srcId, p.payload);

                synchronized (keyExchangeInitiated) {
                    if (!keyExchangeInitiated.contains(srcId)) {
                        keyExchangeInitiated.add(srcId);
                        sendKeyExchange(p.srcId);
                    }
                }

                log("key exchange completed with " + srcId);
                return;
            }

            byte[] decrypted;
            try {
                decrypted = crypto.decrypt(p.payload, srcId);
            } catch (Exception e) {
                log("decrypt failed from " + srcId);
                return;
            }
            String msg = new String(decrypted);

            switch (baseType) {
                case MessageType.CHAT:
                    log("CHAT from " + srcId + " [" + crypto.getStats(srcId) + "]: " + msg);
                    sendAck(p.srcId, p.seq);
                    break;
                case MessageType.CMD:
                    log(" CMD from " + srcId + ": " + msg);
                    handleCommand(msg);
                    sendAck(p.srcId, p.seq);
                    break;
                case MessageType.ACK:
                    log("ACK from " + srcId + " for seq=" + p.seq);
                    break;
                default:
                    log("unknown type=" + baseType);
                    break;
            }

        } else if (p.ttl > 0 && !MessageType.isNoForward(p.type)) {
            Packet fwd = new Packet(p.srcId, p.dstId, p.type,
                    (byte) (p.ttl - 1), p.seq, p.payload);
            radio.send(fwd);
            log("forwarded src=" + (p.srcId & 0xFF) + " dst=" + (p.dstId & 0xFF));
        }
    }

    @Override
    public void run() {
        while (running) {
            try {
                Packet p = radio.receiveBlocking();
                handle(p);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log("error: " + e.getMessage());
            }
        }
    }
}
