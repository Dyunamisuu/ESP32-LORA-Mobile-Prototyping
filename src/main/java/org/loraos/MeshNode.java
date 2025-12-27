package org.loraos;

public class MeshNode implements Runnable {
    private final byte id;
    private final LoRaInterface radio;
    private volatile boolean running = true;
    private int seq = 0;

    public MeshNode(byte id, LoRaInterface radio) {
        this.id = id;
        this.radio = radio;
    }

    public void stop() { running = false; }

    public void sendTo(byte dst, String text, byte type, byte ttl) throws Exception {
        byte[] payload = text.getBytes();
        Packet p = new Packet(id, dst, type, ttl, nextSeq(), payload);
        radio.send(p);
        System.out.println("Node " + (id & 0xFF) + " sent: " + p);
    }

    private synchronized int nextSeq() { return seq++; }

    private void handle(Packet p) throws Exception {
        if (p.dstId == id) {
            System.out.println("Node " + (id & 0xFF) + " received: " + p);
            // hier: app‑logica (gps, cmd, ack, etc.)
        } else if (p.ttl > 0) {
            // eenvoudige forwarding
            Packet fwd = new Packet(p.srcId, p.dstId, p.type, (byte)(p.ttl - 1), p.seq, p.payload);
            radio.send(fwd);
            System.out.println("Node " + (id & 0xFF) + " forwarded: " + fwd);
        } else {
            System.out.println("Node " + (id & 0xFF) + " dropped (TTL=0): " + p);
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
                e.printStackTrace();
            }
        }
    }
}
