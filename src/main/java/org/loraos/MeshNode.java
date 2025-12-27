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

    // vervang later
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
        byte baseType = MessageType.CHAT;
        byte finalType = privateDirect
                ? MessageType.withNoForward(baseType)  // geen hops
                : baseType;                            // normale mesh

        sendTo(dst, text, finalType, (byte) 5);
    }

    public void stop() { running = false; }

    private void log(String msg) {
        System.out.println("[Node " + (id & 0xFF) + "] " + msg);
    }

    public void sendTo(byte dst, String text, byte type, byte ttl) throws Exception {
        byte[] plainPayload = text.getBytes();

        // Encrypt payload
        byte[] encrypted = crypto.encrypt(plainPayload);
        if (encrypted.length > 255) {
            throw new IllegalArgumentException("Encrypted payload too large for Packet");
        }

        Packet p = new Packet(id, dst, type, ttl, nextSeq(), encrypted);
        radio.send(p);
        log("sent (encrypted): " + p);
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
        byte[] encrypted = crypto.encrypt(plain);
        if (encrypted.length > 255) return;

        Packet ack = new Packet(id, dst, MessageType.ACK, (byte) 3, seqToAck, encrypted);
        radio.send(ack);
        log("sent ACK to " + (dst & 0xFF) + " for seq=" + seqToAck);
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
            byte[] decrypted;
            try {
                decrypted = crypto.decrypt(p.payload);
            } catch (Exception e) {
                log("failed to decrypt packet -> drop");
                return;
            }
            String msg = new String(decrypted);

            byte baseType = MessageType.baseType(p.type);
            switch (baseType) {
                case MessageType.CHAT:
                    log("CHAT: " + msg);
                    // stuur ACK terug
                    sendAck(p.srcId, p.seq);
                    break;
                case MessageType.CMD:
                    handleCommand(msg);
                    sendAck(p.srcId, p.seq);
                    break;
                case MessageType.ACK:
                    log("ACK received for seq=" + p.seq + " from " + (p.srcId & 0xFF));
                    break;
                default:
                    log("UNKNOWN type=" + baseType + " msg=" + msg);
                    break;
            }

        } else if (p.ttl > 0 && !MessageType.isNoForward(p.type)) {
            Packet fwd = new Packet(p.srcId, p.dstId, p.type,
                    (byte) (p.ttl - 1), p.seq, p.payload);
            radio.send(fwd);
            log("forwarded: " + fwd);

        } else if (MessageType.isNoForward(p.type)) {
            log("not forwarded (NO_FORWARD flag): " + p);

        } else {
            log("dropped (TTL=0): " + p);
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
