package org.loraos;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public class LoRaChannel {
    private final CopyOnWriteArrayList<LoRaInterface> radios = new CopyOnWriteArrayList<>();
    private final double packetLoss = 0.1; // 10% verlies
    private final long avgLatencyMs = 50;

    public void register(LoRaInterface radio) {
        radios.add(radio);
    }

    public void sendFrom(LoRaInterface sender, Packet packet) {
        new Thread(() -> {
            try {
                Thread.sleep((long)(Math.random() * avgLatencyMs * 2));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }

            for (LoRaInterface receiver : radios) {
                if (receiver == sender) continue;

                if (Math.random() > packetLoss) {
                    try {
                        ((ChannelRadio) receiver).deliver(packet);
                    } catch (Exception e) {
                        System.err.println("Failed to deliver packet to receiver: " + e.getMessage());
                    }
                }
            }
        }).start();
    }
}
